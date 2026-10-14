package ae2nei.bridge;

import ae2nei.core.AE2NEITransformer;
import java.util.zip.*;
import java.io.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import io.netty.buffer.*;
import java.nio.charset.StandardCharsets;

public class Verify {
    static int assertions;
    static void check(boolean ok,String text){assertions++;if(!ok)throw new AssertionError(text);}
    static byte[] bytes(ZipFile z,String path)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=z.getInputStream(z.getEntry(path))){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)b.write(buf,0,n);}return b.toByteArray();}
    static int calls(ClassNode c,String method){int n=0;for(MethodNode m:(List<MethodNode>)c.methods)for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(call.owner.startsWith("ae2nei/")&&call.name.equals(method))n++;}return n;}
    static ClassNode transform(String jar,String name)throws Exception{
        try(ZipFile z=new ZipFile(jar)){byte[] original=bytes(z,name.replace('.','/')+".class");byte[] output=new AE2NEITransformer().transform(name,name,original);check(!Arrays.equals(original,output),"actual bytes must change: "+name);ClassNode c=new ClassNode();new ClassReader(output).accept(c,0);return c;}
    }
    public static void main(String[] args)throws Exception{
        ClassNode inventory=transform(args[0],"codechicken.nei.recipe.AutoCraftingManager");
        check(calls(inventory,"appendTerminalInventoryItems")==1,"exact inventory hook");
        ClassNode worker=transform(args[0],"codechicken.nei.recipe.AutoCraftingManager$1");
        check(calls(worker,"canCraft")==1&&calls(worker,"craft")==1&&calls(worker,"beginProcessing")==1,"all worker hooks must be present");
        ClassNode slot=transform(args[1],"appeng.container.slot.SlotCraftingTerm");
        check(calls(slot,"cap")==1,"native craft cap hook must exist");
        check(ServerCrafting.cap(16)==16,"ordinary shift not limited");
        java.lang.reflect.Field f=ServerCrafting.class.getDeclaredField("LIMIT");f.setAccessible(true);ThreadLocal<Integer> limit=(ThreadLocal<Integer>)f.get(null);
        limit.set(3);check(ServerCrafting.cap(16)==3,"requested count enforced");check(ServerCrafting.cap(2)==2,"native stack cap retained");limit.remove();
        byte[] payload=new byte[65555];new Random(7).nextBytes(payload);
        BridgeNetwork.Result result=new BridgeNetwork.Result(0,3,payload);BridgeNetwork.Pending pending=new BridgeNetwork.Pending();
        int parts=(payload.length+BridgeNetwork.CHUNK-1)/BridgeNetwork.CHUNK;
        for(int part=parts-1;part>=0;part--){
            byte[] data=Arrays.copyOfRange(payload,part*BridgeNetwork.CHUNK,Math.min(payload.length,(part+1)*BridgeNetwork.CHUNK));
            BridgeNetwork.Reply reply=new BridgeNetwork.Reply(123,result,part,parts,data);
            ByteBuf b=Unpooled.buffer();reply.toBytes(b);BridgeNetwork.Reply decoded=new BridgeNetwork.Reply();decoded.fromBytes(b);check(b.readableBytes()==0,"reply consumes payload");b.release();
            pending.add(decoded);pending.add(decoded);
        }
        check(pending.future.isDone()&&Arrays.equals(pending.future.get().snapshot,payload),"reordered chunks and duplicates reconstruct once");
        BridgeNetwork.Request req=new BridgeNetwork.Request(2,5,3,new byte[]{1,2,3},new byte[]{4});req.id=123;
        ByteBuf b=Unpooled.buffer();req.toBytes(b);BridgeNetwork.Request dec=new BridgeNetwork.Request();dec.fromBytes(b);check(dec.id==123&&dec.window==5&&dec.amount==3&&Arrays.equals(req.recipe,dec.recipe),"request roundtrip");b.release();
        for(int size:new int[]{-1,30001,Integer.MAX_VALUE}){b=Unpooled.buffer();b.writeInt(size);boolean rejected=false;try{BridgeNetwork.readBytes(b,30000);}catch(IllegalArgumentException e){rejected=true;}finally{b.release();}check(rejected,"malformed lengths rejected");}
        AE2NEICraftingBridge.rememberSnapshot(payload);
        check(AE2NEICraftingBridge.takeSnapshot()==payload&&AE2NEICraftingBridge.takeSnapshot()==null,"server observation consumed once; no persistent inventory cache");
        AE2NEICraftingBridge.rememberSnapshot(payload);AE2NEICraftingBridge.beginProcessing();
        check(AE2NEICraftingBridge.takeSnapshot()==null,"new chain discards prior observation");
        Access sample=new Access();
        Reflect.set(sample,7,"missing","value");
        check(((Number)Reflect.get(sample,"missing","value")).intValue()==7,"cached inherited private field aliases");
        check(Reflect.call(sample,"overload",3).equals("int")&&Reflect.call(sample,"overload","x").equals("String")&&Reflect.call(sample,"overload",4).equals("int"),"cached reflection distinguishes overloads by argument types");
        PrintStream prior=System.err;ByteArrayOutputStream warnings=new ByteArrayOutputStream();
        try{System.setErr(new PrintStream(warnings,true,"UTF-8"));for(int i=0;i<20;i++)BridgeLog.warning("audit-spam","audit warning",null);}
        finally{System.setErr(prior);}
        check(new String(warnings.toByteArray(),StandardCharsets.UTF_8).split("audit warning",-1).length==2,"twenty repeated errors produce one warning within rate window");
        boundedNbt(); cancellation(); capacity(); goals(); notice();
        ae2nei.core.AE2NEIMod mod=new ae2nei.core.AE2NEIMod();
        check(mod.compatible(Collections.singletonMap("ae2neiautocraftbridge","1.1.5"),cpw.mods.fml.relauncher.Side.SERVER),"matching new protocol accepted");
        check(!mod.compatible(Collections.singletonMap("ae2neiautocraftbridge","1.1.4"),cpw.mods.fml.relauncher.Side.CLIENT),"older bridge peer rejected by version handshake");
        System.out.println("PASS: "+assertions+" bytecode/protocol assertions. Native gameplay is checked separately.");
    }
    static byte[] gzip(byte[] raw)throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(GZIPOutputStream zip=new GZIPOutputStream(out)){zip.write(raw);}return out.toByteArray();
    }
    static void reject(byte[] raw,int limit,String description)throws Exception {
        boolean failed=false;try{BoundedNbt.unpack(gzip(raw),limit);}catch(IOException expected){failed=true;}
        check(failed,description);
    }
    static byte[] byteArray(int declared,int actual)throws Exception {
        ByteArrayOutputStream raw=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(raw);
        d.writeByte(10);d.writeUTF("");d.writeByte(7);d.writeUTF("x");d.writeInt(declared);d.write(new byte[actual]);d.writeByte(0);return raw.toByteArray();
    }
    static void boundedNbt()throws Exception {
        byte[] good=byteArray(8,8);
        check(Arrays.equals(good,BoundedNbt.unpack(gzip(good),good.length)),"bounded NBT accepts exact byte limit");
        reject(good,good.length-1,"NBT rejects expanded bytes over limit");
        reject(byteArray(3*1024*1024,3*1024*1024),BoundedNbt.STACK_BYTES,"small compressed bomb cannot bypass stack expansion limit");
        reject(byteArray(Integer.MAX_VALUE,0),BoundedNbt.STACK_BYTES,"declared huge array rejected before Minecraft allocation");
        reject(byteArray(-1,0),BoundedNbt.STACK_BYTES,"negative NBT array rejected");
        reject(new byte[]{10,0,0,0,1},100,"trailing NBT bytes rejected");
        ByteArrayOutputStream raw=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(raw);
        d.writeByte(10);d.writeUTF("");for(int i=0;i<33;i++){d.writeByte(10);d.writeUTF("x");}for(int i=0;i<34;i++)d.writeByte(0);
        reject(raw.toByteArray(),1000,"deep NBT rejected before recursive native parse");
        raw.reset();d.writeByte(10);d.writeUTF("");d.writeByte(9);d.writeUTF("x");d.writeByte(1);d.writeInt(Integer.MAX_VALUE);d.writeByte(0);
        reject(raw.toByteArray(),1000,"declared huge NBT list rejected");
    }
    static void cancellation()throws Exception {
        Object player=new Object(),other=new Object();
        BridgeNetwork.Request first=new BridgeNetwork.Request(0,1,0,new byte[0],new byte[0]);first.id=1;
        check(BridgeNetwork.enqueue(first,player)==0,"first request admitted");
        java.lang.reflect.Field field=BridgeNetwork.class.getDeclaredField("SERVER");field.setAccessible(true);
        Map<Object,BridgeNetwork.ServerTask> queue=(Map<Object,BridgeNetwork.ServerTask>)field.get(null);
        BridgeNetwork.ServerTask current=queue.get(player);
        BridgeNetwork.cancel(1,other);check(!current.cancelled,"cancel scoped to authenticated player");
        BridgeNetwork.Request second=new BridgeNetwork.Request(0,1,0,new byte[0],new byte[0]);second.id=2;
        check(BridgeNetwork.enqueue(second,player)==6,"busy request receives explicit rejection");
        BridgeNetwork.cancel(1,player);check(current.cancelled,"accepted cancel marks active task");
        check(BridgeNetwork.enqueue(second,player)==0&&queue.get(player)==current&&current.next!=null,"restart waits behind cancelled task cleanup");
        check(BridgeNetwork.enqueue(first,player)==-1,"old IDs cannot restart inventory mutation");
        BridgeNetwork.ServerTask successor=current.next;BridgeNetwork.retire(current);
        check(queue.get(player)==successor,"cleanup promotes successor without losing it");
        BridgeNetwork.retire(current);check(queue.get(player)==successor,"late retirement cannot remove successor");
        BridgeNetwork.Cancel cancel=new BridgeNetwork.Cancel(2);ByteBuf bytes=Unpooled.buffer();cancel.toBytes(bytes);
        BridgeNetwork.Cancel decoded=new BridgeNetwork.Cancel();decoded.fromBytes(bytes);check(decoded.id==2&&bytes.readableBytes()==0,"cancel protocol roundtrip");bytes.release();
        BridgeNetwork.cancel(2,player);check(successor.cancelled,"queued successor can also be cancelled");BridgeNetwork.retire(successor);
        check(!queue.containsKey(player),"cancelled queue completely released");
    }
    static void capacity()throws Exception {
        FakeStack stack=new FakeStack(1,64);FakeAdaptor inventory=new FakeAdaptor(2);
        check(ServerCrafting.fittingCrafts(inventory,stack,4)==2,"partial capacity reduces batch to two crafts");
        check(stack.stackSize==1,"capacity probe never mutates expected output");
        check(ServerCrafting.fittingCrafts(new FakeAdaptor(7),new FakeStack(4,64),4)==1,"capacity respects multi-item recipe output");
        check(ServerCrafting.fittingCrafts(new FakeAdaptor(0),stack,4)==0,"full inventory performs no native craft");
        check(ServerCrafting.fittingCrafts(new FakeAdaptor(4),new FakeStack(1,1),4)==1,"unstackable native single-craft cap retained");
    }
    public static class Goal {
        public final Object itemStack,recipeId;
        Goal(Object item,Object id){itemStack=item;recipeId=id;}
    }
    public static class RootPlan {
        private static final Object ROOT_RECIPE_ID=new Object();
        public final List<Goal> initialItems=new ArrayList<Goal>(),recipeIngredients=new ArrayList<Goal>();
        public boolean hasMasterRoot(){return true;}
    }
    static void goals()throws Exception {
        RootPlan plan=new RootPlan();plan.initialItems.add(new Goal("ordinary",null));
        plan.recipeIngredients.add(new Goal("target",RootPlan.ROOT_RECIPE_ID));
        plan.recipeIngredients.add(new Goal("intermediate",new Object()));
        List<Object> outputs=AE2NEICraftingBridge.goalStacks(plan);
        check(outputs.contains("target"),"master-root target recognized when initialItems only contains unrelated bookmark");
        check(!outputs.contains("ordinary")&&!outputs.contains("intermediate"),"iterator inventory and non-root ingredients cannot become output goals");
        plan.recipeIngredients.add(new Goal("second target",RootPlan.ROOT_RECIPE_ID));
        check(AE2NEICraftingBridge.goalStacks(plan).contains("second target"),"multiple master-root outputs all retained");
        check(AE2NEICraftingBridge.goalStacks(null).isEmpty(),"unknown goal list cannot authorize output relocation");
    }
    public static class NoticeItem { public String getDisplayName(){return "Steel Plate";} }
    public static class NoticePosition { public Object item=new NoticeItem(); }
    public static class NoticeHandler {
        public Object result=new NoticePosition();
        public Object getResultStack(int index){return result;}
        public String getRecipeName(){return "Metal Bender";}
    }
    public static class NoticeRef { public final NoticeHandler handler=new NoticeHandler();public final int recipeIndex=0; }
    static void notice() throws Exception {
        NoticeRef ref=new NoticeRef();String[] names=AE2NEICraftingBridge.unsupportedRecipe(ref);
        check(Arrays.equals(names,new String[]{"Steel Plate","Metal Bender"}),"notice names the actual item and bookmarked recipe handler");
        ref.handler.result=null;
        check(AE2NEICraftingBridge.unsupportedRecipe(ref)==null,"missing recipe output does not fabricate a machine notice");
        java.lang.reflect.Field f=AE2NEICraftingBridge.class.getDeclaredField("NOTIFIED");f.setAccessible(true);
        ThreadLocal<Boolean> notified=(ThreadLocal<Boolean>)f.get(null);notified.set(true);AE2NEICraftingBridge.beginProcessing();
        check(notified.get()==null,"a new NEI chain resets its one-notice limit");
    }
    public static class FakeStack {
        public int stackSize;final int max;FakeStack(int count,int max){stackSize=count;this.max=max;}
        public FakeStack copy(){return new FakeStack(stackSize,max);}public int getMaxStackSize(){return max;}
    }
    public static class FakeAdaptor {
        final int room;FakeAdaptor(int room){this.room=room;}
        public Object simulateAdd(FakeStack stack){return stack.stackSize<=room?null:stack;}
    }
    public static class Parent { private int value; }
    public static class Access extends Parent {
        public String overload(int n){return "int";}
        public String overload(String n){return "String";}
    }
}
