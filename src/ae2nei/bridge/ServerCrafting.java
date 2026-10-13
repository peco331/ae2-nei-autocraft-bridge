package ae2nei.bridge;

import java.util.List;

/** Native crafting, bounded across ticks; only real inventories are rearranged at completion. */
public final class ServerCrafting {
    private static final ThreadLocal<Integer> LIMIT = new ThreadLocal<Integer>();
    private ServerCrafting() {}
    public static int cap(int vanillaLimit) {
        Integer requested = LIMIT.get();
        return requested == null ? vanillaLimit : Math.min(vanillaLimit, requested);
    }
    /** Synchronous entry for the isolated harness. Network jobs use advance once per tick. */
    public static BridgeNetwork.Result execute(BridgeNetwork.Request req, Object player) {
        Job job = new Job(req, player);
        BridgeNetwork.Result result;
        do { result = job.advance(4); } while (result == null);
        return result;
    }
    public static final class Job {
        final BridgeNetwork.Request req;
        final Object player;
        Object container, expected, packet;
        int completed;
        boolean initialized, touched;
        public Job(BridgeNetwork.Request req, Object player) { this.req = req; this.player = player; }
        public BridgeNetwork.Result abort() {
            try { return finish(5, false); }
            catch (Exception e) { AE2NEICraftingBridge.report("abort cleanup", e); return new BridgeNetwork.Result(5, completed, new byte[0]); }
        }
        /** null means the same authenticated request continues next tick, never a client retry. */
        public BridgeNetwork.Result advance(int slice) {
            try {
                Object current = Reflect.get(player, "field_71070_bA", "openContainer");
                if (!Reflect.is(current, "appeng.container.implementations.ContainerCraftingTerm")
                    || ((Number) Reflect.get(current, "field_75152_c", "windowId")).intValue() != req.window
                    || (container != null && current != container)
                    || !Boolean.TRUE.equals(Reflect.call(current, "func_75145_c|canInteractWith", player)))
                    return new BridgeNetwork.Result(1, completed, new byte[0]);
                container = current;
                Object node = Reflect.call(container, "getNetworkNode");
                if (node == null) return finish(2, false);
                Object grid = Reflect.call(node, "getGrid");
                Object security = Reflect.call(grid, "getCache", Class.forName("appeng.api.networking.security.ISecurityGrid"));
                Object energy = Reflect.call(grid, "getCache", Class.forName("appeng.api.networking.energy.IEnergyGrid"));
                if (!Boolean.TRUE.equals(Reflect.call(security, "hasPermission", player, enumValue("appeng.api.config.SecurityPermissions", "EXTRACT")))
                    || !Boolean.TRUE.equals(Reflect.call(energy, "isNetworkPowered"))) return finish(2, false);
                if (req.operation == 0) return finish(0, true);
                if ((req.operation != 1 && req.operation != 2) || req.amount < 1 || req.amount > 64 || slice < 1) return finish(3, false);
                Object output = Reflect.get(container, "outputSlot");
                if (!Reflect.is(output, "appeng.container.slot.SlotCraftingTerm")) return finish(3, false);
                if (!initialized) {
                    expected = InventorySnapshot.readStack(InventorySnapshot.expand(req.expected, BoundedNbt.STACK_BYTES));
                    if (expected == null || InventorySnapshot.size(expected) <= 0) return finish(3, false);
                    // Only the incoming ByteBuf constructor populates native recipe[].
                    InventorySnapshot.validateRecipe(req.recipe);
                    io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.wrappedBuffer(req.recipe);
                    try { packet = Class.forName("appeng.core.sync.packets.PacketNEIRecipe").getConstructor(io.netty.buffer.ByteBuf.class).newInstance(buffer); }
                    finally { buffer.release(); }
                    initialized = true;
                }
                touched = true;
                Reflect.call(packet, "serverPacketData", null, packet, player);
                Object actual = Reflect.call(output, "func_75211_c|getStack");
                if (!same(expected, actual) || InventorySnapshot.size(expected) != InventorySnapshot.size(actual)) return finish(0, true);
                compactOutput(player, expected);
                long before = playerCount(container, player, expected);
                int attempts = Math.min(slice, req.amount - completed);
                Object adaptor = Reflect.call(Class.forName("appeng.util.InventoryAdaptor"), "getAdaptor", player, null);
                attempts = fittingCrafts(adaptor, expected, attempts);
                if (attempts == 0) return finish(0, true);
                LIMIT.set(attempts);
                try { Reflect.call(output, "doClick", enumValue("appeng.helpers.InventoryAction", "CRAFT_SHIFT"), player); }
                finally { LIMIT.remove(); }
                long gained = playerCount(container, player, expected) - before;
                int perCraft = InventorySnapshot.size(expected);
                if (gained < 0 || gained % perCraft != 0 || gained / perCraft > attempts) return finish(4, false);
                completed += (int) (gained / perCraft);
                compactOutput(player, expected);
                // Account for native output before routing it; a later snapshot observes its real destination.
                if (req.operation == 2 && gained > 0) depositCraftedOutput(container, player, expected, gained);
                if (gained == 0 || completed >= req.amount) return finish(0, true);
                return null;
            } catch (Exception e) {
                AE2NEICraftingBridge.report("server request", e);
                try { return finish(4, false); }
                catch (Exception cleanup) { AE2NEICraftingBridge.report("cleanup", cleanup); return new BridgeNetwork.Result(4, completed, new byte[0]); }
            } finally { LIMIT.remove(); }
        }
        private BridgeNetwork.Result finish(int status, boolean snapshot) throws Exception {
            // Failed/closed containers never have another container's grid cleared.
            if (touched && container == Reflect.get(player, "field_71070_bA", "openContainer")) returnRemainders(container, player);
            return new BridgeNetwork.Result(status, completed, snapshot ? InventorySnapshot.capture(container, player) : new byte[0]);
        }
    }
    /** Native shift-click preflights its whole batch; reduce it to available output space. */
    public static int fittingCrafts(Object adaptor, Object expected, int requested) throws Exception {
        if (adaptor == null || InventorySnapshot.size(expected) <= 0) return 0;
        int perCraft = InventorySnapshot.size(expected);
        int max = ((Number) Reflect.call(expected, "func_77976_d|getMaxStackSize")).intValue();
        for (int n = Math.min(requested, max / perCraft); n > 0; n--) {
            Object batch = Reflect.call(expected, "func_77946_l|copy");
            Reflect.set(batch, perCraft * n, "field_77994_a", "stackSize");
            if (Reflect.call(adaptor, "simulateAdd", batch) == null) return n;
        }
        return 0;
    }
    /** Route only this native slice's new intermediate output; preserve pre-existing inventory quantities. */
    public static void depositCraftedOutput(Object container, Object player, Object expected, long quantity) throws Exception {
        if (quantity <= 0) return;
        Object node = Reflect.call(container, "getNetworkNode");
        if (node == null) return;
        Object grid = Reflect.call(node, "getGrid");
        Object security = Reflect.call(grid, "getCache", Class.forName("appeng.api.networking.security.ISecurityGrid"));
        Object energy = Reflect.call(grid, "getCache", Class.forName("appeng.api.networking.energy.IEnergyGrid"));
        if (!Boolean.TRUE.equals(Reflect.call(security, "hasPermission", player, enumValue("appeng.api.config.SecurityPermissions", "INJECT")))
            || !Boolean.TRUE.equals(Reflect.call(energy, "isNetworkPowered"))) return;
        Object filter = InventorySnapshot.filter(container);
        Object type = Reflect.call(Class.forName("appeng.util.item.AEItemStack"), "create", expected);
        // Hidden ME items cannot be counted by the next NEI iteration, so keep them in the player inventory.
        if (filter != null && !Boolean.TRUE.equals(Reflect.call(filter, "isListed", type))) return;
        Object storage = InventorySnapshot.storage(container);
        Object source = Reflect.call(container, "getActionSource");
        Object inventory = Reflect.get(player, "field_71071_by", "inventory");
        boolean changed = false;
        for (int i = 0; i < 36 && quantity > 0; i++) {
            Object stack = Reflect.call(inventory, "func_70301_a|getStackInSlot", i);
            if (!same(expected, stack)) continue;
            int offered = (int) Math.min(quantity, InventorySnapshot.size(stack));
            if (offered <= 0) continue;
            Object copy = Reflect.call(stack, "func_77946_l|copy");
            Reflect.set(copy, offered, "field_77994_a", "stackSize");
            Object ae = Reflect.call(Class.forName("appeng.util.item.AEItemStack"), "create", copy);
            Object rejected = Reflect.call(Class.forName("appeng.util.Platform"), "poweredInsert", energy, storage, ae, source);
            int accepted = offered - (rejected == null ? 0 : ((Number) Reflect.call(rejected, "getStackSize")).intValue());
            if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid ME insertion result");
            if (accepted > 0) {
                int left = InventorySnapshot.size(stack) - accepted;
                Reflect.set(stack, left, "field_77994_a", "stackSize");
                Reflect.call(inventory, "func_70299_a|setInventorySlotContents", i, left == 0 ? null : stack);
                changed = true;
            }
            quantity -= offered;
        }
        if (changed) Reflect.call(inventory, "func_70296_d|markDirty");
    }
    /** Merge only identical output stacks; retain the first matching slot and every unrelated item. */
    public static void compactOutput(Object player, Object expected) throws Exception {
        Object inventory = Reflect.get(player, "field_71071_by", "inventory");
        int max = Math.min(((Number) Reflect.call(inventory, "func_70297_j_|getInventoryStackLimit")).intValue(),
            ((Number) Reflect.call(expected, "func_77976_d|getMaxStackSize")).intValue());
        if (max <= 1) return;
        boolean changed = false;
        for (int i = 0; i < 36; i++) {
            Object target = Reflect.call(inventory, "func_70301_a|getStackInSlot", i);
            if (!same(expected, target)) continue;
            int room = max - InventorySnapshot.size(target);
            for (int j = i + 1; j < 36 && room > 0; j++) {
                Object source = Reflect.call(inventory, "func_70301_a|getStackInSlot", j);
                if (!same(target, source)) continue;
                int take = Math.min(room, InventorySnapshot.size(source));
                if (take <= 0) continue;
                Reflect.set(target, InventorySnapshot.size(target) + take, "field_77994_a", "stackSize");
                int left = InventorySnapshot.size(source) - take;
                Reflect.set(source, left, "field_77994_a", "stackSize");
                Reflect.call(inventory, "func_70299_a|setInventorySlotContents", j, left == 0 ? null : source);
                room -= take; changed = true;
            }
        }
        if (changed) Reflect.call(inventory, "func_70296_d|markDirty");
    }
    /** Return real matrix contents with native powered insertion; any rejected remainder stays reachable. */
    public static void returnRemainders(Object container, Object player) throws Exception {
        Object matrix = Reflect.call(container, "getInventoryByName", "crafting");
        Object node = Reflect.call(container, "getNetworkNode");
        Object grid = node == null ? null : Reflect.call(node, "getGrid");
        Object security = grid == null ? null : Reflect.call(grid, "getCache", Class.forName("appeng.api.networking.security.ISecurityGrid"));
        Object energy = grid == null ? null : Reflect.call(grid, "getCache", Class.forName("appeng.api.networking.energy.IEnergyGrid"));
        boolean insert = security != null && energy != null
            && Boolean.TRUE.equals(Reflect.call(security, "hasPermission", player, enumValue("appeng.api.config.SecurityPermissions", "INJECT")))
            && Boolean.TRUE.equals(Reflect.call(energy, "isNetworkPowered"));
        Object storage = insert ? InventorySnapshot.storage(container) : null;
        Object adaptor = Reflect.call(Class.forName("appeng.util.InventoryAdaptor"), "getAdaptor", player, null);
        for (int i = 0; i < 9; i++) {
            Object stack = Reflect.call(matrix, "func_70301_a|getStackInSlot", i);
            if (stack == null || InventorySnapshot.size(stack) <= 0) continue;
            Object left = stack;
            if (insert) {
                Object ae = Reflect.call(Class.forName("appeng.util.item.AEItemStack"), "create", stack);
                Object rejected = Reflect.call(Class.forName("appeng.util.Platform"), "poweredInsert", energy, storage, ae, Reflect.call(container, "getActionSource"));
                left = rejected == null ? null : Reflect.call(rejected, "getItemStack");
                Reflect.call(matrix, "func_70299_a|setInventorySlotContents", i, left);
            }
            if (left != null && adaptor != null && Reflect.call(adaptor, "simulateAdd", left) == null) {
                left = Reflect.call(adaptor, "addItems", left);
                Reflect.call(matrix, "func_70299_a|setInventorySlotContents", i, left);
            }
        }
        Reflect.call(container, "func_75130_a|onCraftMatrixChanged", matrix);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object enumValue(String type, String name) throws Exception { return Enum.valueOf((Class) Class.forName(type), name); }
    static boolean same(Object a, Object b) throws Exception {
        return a != null && b != null && Boolean.TRUE.equals(Reflect.call(Class.forName("appeng.util.Platform"), "isSameItemPrecise", a, b));
    }
    static long playerCount(Object container, Object player, Object expected) throws Exception {
        Object inventory = Reflect.get(player, "field_71071_by", "inventory");
        long amount = 0;
        for (Object slot : (List<?>) Reflect.get(container, "field_75151_b", "inventorySlots")) {
            if (Reflect.get(slot, "field_75224_c", "inventory") == inventory) {
                Object stack = Reflect.call(slot, "func_75211_c|getStack");
                if (same(expected, stack)) amount += InventorySnapshot.size(stack);
            }
        }
        return amount;
    }
}
