package ae2nei.bridge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, correlated requests. All game access occurs on the appropriate tick thread. */
public final class BridgeNetwork {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int CHUNK = 24000;
    private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(8);
    private static final AtomicLong NEXT = new AtomicLong();
    private static final ConcurrentHashMap<Long, Pending> PENDING = new ConcurrentHashMap<Long, Pending>();
    private static final ConcurrentLinkedQueue<Runnable> CLIENT = new ConcurrentLinkedQueue<Runnable>();
    private static final Map<Object, ServerTask> SERVER = new LinkedHashMap<Object, ServerTask>();
    private static final Map<Object, Long> LAST = new WeakHashMap<Object, Long>();
    private static SimpleNetworkWrapper channel;

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("ae2neibridge");
        channel.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        channel.registerMessage(ReplyHandler.class, Reply.class, 1, Side.CLIENT);
        channel.registerMessage(CancelHandler.class, Cancel.class, 2, Side.SERVER);
        FMLCommonHandler.instance().bus().register(new BridgeNetwork());
    }

    public static final class Result {
        public final int status, completed;
        public final byte[] snapshot;
        public Result(int status, int completed, byte[] snapshot) {
            this.status = status; this.completed = completed; this.snapshot = snapshot;
        }
    }
    public static final class Request implements IMessage {
        public long id;
        public int operation, window, amount;
        public byte[] recipe = new byte[0], expected = new byte[0];
        public Request() {}
        public Request(int operation, int window, int amount, byte[] recipe, byte[] expected) {
            this.operation = operation; this.window = window; this.amount = amount; this.recipe = recipe; this.expected = expected;
        }
        public void toBytes(ByteBuf b) {
            b.writeLong(id); b.writeInt(operation); b.writeInt(window); b.writeInt(amount);
            writeBytes(b, recipe); writeBytes(b, expected);
        }
        public void fromBytes(ByteBuf b) {
            id = b.readLong(); operation = b.readInt(); window = b.readInt(); amount = b.readInt();
            recipe = readBytes(b, 30000); expected = readBytes(b, 4096);
        }
    }
    public static final class Reply implements IMessage {
        public long id;
        public int status, completed, part, parts;
        public byte[] data = new byte[0];
        public Reply() {}
        Reply(long id, Result result, int part, int parts, byte[] data) {
            this.id = id; status = result.status; completed = result.completed; this.part = part; this.parts = parts; this.data = data;
        }
        public void toBytes(ByteBuf b) {
            b.writeLong(id); b.writeInt(status); b.writeInt(completed); b.writeInt(part); b.writeInt(parts); writeBytes(b, data);
        }
        public void fromBytes(ByteBuf b) {
            id = b.readLong(); status = b.readInt(); completed = b.readInt(); part = b.readInt(); parts = b.readInt();
            data = readBytes(b, CHUNK);
            if (parts < 1 || parts > (MAX_BYTES + CHUNK - 1) / CHUNK || part < 0 || part >= parts || completed < 0 || completed > 64)
                throw new IllegalArgumentException("Invalid reply metadata");
        }
    }
    public static final class Cancel implements IMessage {
        public long id;
        public Cancel() {}
        public Cancel(long id) { this.id = id; }
        public void toBytes(ByteBuf b) { b.writeLong(id); }
        public void fromBytes(ByteBuf b) { id = b.readLong(); }
    }
    static void writeBytes(ByteBuf b, byte[] bytes) { b.writeInt(bytes.length); b.writeBytes(bytes); }
    static byte[] readBytes(ByteBuf b, int limit) {
        int size = b.readInt();
        if (size < 0 || size > limit || size > b.readableBytes()) throw new IllegalArgumentException("Invalid payload size");
        byte[] result = new byte[size]; b.readBytes(result); return result;
    }
    static final class Pending {
        final CompletableFuture<Result> future = new CompletableFuture<Result>();
        byte[][] parts;
        int status, completed, received, bytes;
        synchronized void add(Reply reply) {
            if (parts == null) { parts = new byte[reply.parts][]; status = reply.status; completed = reply.completed; }
            if (parts.length != reply.parts || status != reply.status || completed != reply.completed) {
                future.completeExceptionally(new IllegalArgumentException("Mismatched reply")); return;
            }
            if (parts[reply.part] != null) return;
            parts[reply.part] = reply.data; bytes += reply.data.length; received++;
            if (bytes > MAX_BYTES) { future.completeExceptionally(new IllegalArgumentException("Oversized reply")); return; }
            if (received == parts.length) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                for (byte[] part : parts) out.write(part, 0, part.length);
                future.complete(new Result(status, completed, out.toByteArray()));
            }
        }
    }
    public static Result request(final Object gui, final Callable<Request> builder) throws Exception {
        if (channel == null || !AE2NEICraftingBridge.active(gui)) return null;
        final long id = NEXT.incrementAndGet();
        final Pending pending = new Pending();
        final long started = System.nanoTime();
        if (!PENDING.isEmpty()) return null; // one NEI chain, no concurrent inventory actions
        PENDING.put(id, pending);
        CLIENT.add(new Runnable() {
            public void run() {
                if (!PENDING.containsKey(id) || !AE2NEICraftingBridge.active(gui) || System.nanoTime() - started >= TIMEOUT) return;
                try {
                    Request request = builder.call();
                    if (request == null) { pending.future.complete(new Result(3, 0, new byte[0])); return; }
                    if (request.recipe.length > 30000 || request.expected.length > 4096) throw new IllegalArgumentException("Recipe packet too large");
                    request.id = id; channel.sendToServer(request);
                } catch (Exception e) { pending.future.completeExceptionally(e); }
            }
        });
        try {
            while (System.nanoTime() - started < TIMEOUT && AE2NEICraftingBridge.active(gui)) {
                try { return pending.future.get(25, TimeUnit.MILLISECONDS); }
                catch (TimeoutException ignored) {}
            }
            BridgeLog.warning("timeout", "Request timed out or cancelled; no automatic retry (id=" + id + ")", null);
            return null;
        } finally {
            // Enqueue before releasing the client slot: a restarted worker sends after this cancel.
            if (!pending.future.isDone()) CLIENT.add(new Runnable() {
                public void run() {
                    try { channel.sendToServer(new Cancel(id)); }
                    catch (Exception e) { AE2NEICraftingBridge.report("cancel send", e); }
                }
            });
            PENDING.remove(id);
        }
    }
    static Object sender(MessageContext context) throws Exception {
        Object handler = Reflect.get(context, "netHandler");
        return Reflect.get(handler, "field_147369_b", "playerEntity");
    }
    /** Admission and cancellation only manipulate queue metadata, never game inventories. */
    static int enqueue(Request request, Object player) {
        synchronized (SERVER) {
            Long last = LAST.get(player);
            if (request.id <= 0 || (last != null && request.id <= last)) return -1;
            ServerTask current = SERVER.get(player);
            if (current != null) {
                if (!current.cancelled || (current.next != null && !current.next.cancelled)) return 6;
                current.next = new ServerTask(request, player);
            } else {
                if (SERVER.size() >= 32) return 6;
                SERVER.put(player, new ServerTask(request, player));
            }
            LAST.put(player, request.id);
            return 0;
        }
    }
    static void cancel(long id, Object player) {
        synchronized (SERVER) {
            ServerTask current = SERVER.get(player);
            if (current == null) return;
            if (current.req.id == id) current.cancelled = true;
            else if (current.next != null && current.next.req.id == id) current.next.cancelled = true;
        }
    }
    static void retire(ServerTask task) {
        synchronized (SERVER) {
            if (SERVER.get(task.player) == task) {
                SERVER.remove(task.player);
                if (task.next != null) SERVER.put(task.player, task.next);
            }
        }
    }
    public static final class CancelHandler implements IMessageHandler<Cancel, IMessage> {
        public IMessage onMessage(Cancel message, MessageContext context) {
            try { cancel(message.id, sender(context)); }
            catch (Exception e) { AE2NEICraftingBridge.report("cancel", e); }
            return null;
        }
    }
    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        public IMessage onMessage(Request request, MessageContext context) {
            try {
                // Reflecting MessageContext's methods also resolves its client-only return types on a dedicated server.
                int admitted = enqueue(request, sender(context));
                if (admitted == 6) return new Reply(request.id, new Result(6, 0, new byte[0]), 0, 1, new byte[0]);
            } catch (Exception e) { AE2NEICraftingBridge.report("receive", e); }
            return null;
        }
    }
    public static final class ReplyHandler implements IMessageHandler<Reply, IMessage> {
        public IMessage onMessage(Reply reply, MessageContext context) {
            Pending pending = PENDING.get(reply.id);
            if (pending != null) pending.add(reply);
            return null;
        }
    }
    static final class ServerTask {
        final Request req; final Object player; final long created = System.nanoTime();
        final ServerCrafting.Job job;
        volatile boolean cancelled;
        ServerTask next; // guarded by SERVER
        ServerTask(Request req, Object player) { this.req = req; this.player = player; job = new ServerCrafting.Job(req, player); }
        void run() {
            Result result = cancelled || System.nanoTime() - created >= TIMEOUT ? job.abort() : job.advance(4);
            if (result == null) return;
            retire(this);
            if (BridgeLog.DEBUG)
                System.out.println("[AE2-NEI-Bridge] Request " + req.id + ": requested=" + req.amount + ", completed=" + result.completed + ", status=" + result.status);
            else if (result.status != 0) BridgeLog.warning("status-" + result.status, "Request ended with status=" + result.status + ", completed=" + result.completed, null);
            int parts = Math.max(1, (result.snapshot.length + CHUNK - 1) / CHUNK);
            try {
                for (int part = 0; part < parts; part++) {
                    byte[] bytes = Arrays.copyOfRange(result.snapshot, part * CHUNK, Math.min(result.snapshot.length, (part + 1) * CHUNK));
                    Reflect.call(channel, "sendTo", new Reply(req.id, result, part, parts, bytes), player);
                }
            } catch (Exception e) { AE2NEICraftingBridge.report("reply", e); }
        }
    }
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Runnable action;
        while ((action = CLIENT.poll()) != null) action.run();
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ServerTask[] tasks;
        synchronized (SERVER) { tasks = SERVER.values().toArray(new ServerTask[0]); }
        long started = System.nanoTime();
        for (ServerTask task : tasks) {
            task.run();
            synchronized (SERVER) {
                if (SERVER.get(task.player) == task) { SERVER.remove(task.player); SERVER.put(task.player, task); }
            }
            // Check between native slices; a single native call may itself exceed this budget.
            if (System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(5)) break;
        }
    }
}
