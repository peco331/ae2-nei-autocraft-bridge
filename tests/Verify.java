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
        BridgeNetwork.Request req=new BridgeNetwork.Request(1,5,3,new byte[]{1,2,3},new byte[]{4});req.id=123;
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
        System.out.println("PASS: "+assertions+" bytecode/protocol assertions. Native gameplay is checked separately.");
    }
    public static class Parent { private int value; }
    public static class Access extends Parent {
        public String overload(int n){return "int";}
        public String overload(String n){return "String";}
    }
}
