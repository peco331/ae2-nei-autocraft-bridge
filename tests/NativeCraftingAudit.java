package ae2nei.test;

import ae2nei.bridge.*;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Isolated-runtime harness: real AE2 container/slot/recipes, a controlled in-memory ME monitor. */
@Mod(modid="ae2neinativeaudit", name="AE2 NEI isolated test", version="1", dependencies="after:ae2neiautocraftbridge")
public class NativeCraftingAudit {
    boolean ready, ran, allowed=true, powered=true;
    boolean injectAllowed=true;
    long insertCapacity=Long.MAX_VALUE;
    Object list, monitor, player, container, itemType;
    final StringBuilder evidence = new StringBuilder();
    final List<Object> added = new ArrayList<Object>();
    static Object call(Object o, String n, Object... a) throws Exception { return Reflect.call(o,n,a); }
    static Object get(Object o,String... n) throws Exception { return Reflect.get(o,n); }
    static Class<?> cls(String n) throws Exception { return Class.forName(n); }
    Object proxy(String[] names, final InvocationHandler handler) throws Exception {
        Class<?>[] interfaces=new Class<?>[names.length]; for(int i=0;i<names.length;i++)interfaces[i]=cls(names[i]);
        return Proxy.newProxyInstance(getClass().getClassLoader(),interfaces,new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] args)throws Throwable {
                if(m.getName().equals("hashCode")) return System.identityHashCode(p);
                if(m.getName().equals("equals"))return p==args[0];
                if(m.getName().equals("toString"))return "AuditProxy";
                Object r=handler.invoke(p,m,args==null?new Object[0]:args);
                if(r!=null)return r;
                if(m.getReturnType()==boolean.class)return false;
                if(m.getReturnType()==int.class)return 0;
                if(m.getReturnType()==long.class)return 0L;
                if(m.getReturnType()==double.class)return 0.0;
                return null;
            }
        });
    }
    Object item(String name)throws Exception{return get(cls("net.minecraft.init.Items"),name);}
    Object stack(Object item,int count)throws Exception{return cls("net.minecraft.item.ItemStack").getConstructor(cls("net.minecraft.item.Item"),int.class,int.class).newInstance(item,count,0);}
    Object ae(Object stack)throws Exception{return call(cls("appeng.util.item.AEItemStack"),"create",stack);}
    void put(Object item,int count)throws Exception{call(list,"addStorage",ae(stack(item,count)));}
    long amount(Object item)throws Exception{
        Object ae=call(list,"findPrecise",ae(stack(item,1)));return ae==null?0:((Number)call(ae,"getStackSize")).longValue();
    }
    void check(boolean ok,String text){evidence.append(ok?"PASS ":"FAIL ").append(text).append('\n');if(!ok)throw new AssertionError(text);}
    @Mod.EventHandler public void started(FMLServerStartedEvent e){ready=true;FMLCommonHandler.instance().bus().register(this);}
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){
        if(!ready||ran||e.phase!=TickEvent.Phase.END)return;ran=true;
        try{run();evidence.append("PASS=true\n");}catch(Throwable t){evidence.append("ERROR=").append(t).append('\n');t.printStackTrace();evidence.append("PASS=false\n");}
        finally{try{Files.write(Paths.get("native-audit.txt"),evidence.toString().getBytes("UTF-8"));}catch(Exception x){x.printStackTrace();}}
    }
    void setup()throws Exception{
        Object server=call(cls("net.minecraft.server.MinecraftServer"),"func_71276_C|getServer");
        Object world=call(server,"func_71218_a|worldServerForDimension",0);
        player=call(cls("net.minecraftforge.common.util.FakePlayerFactory"),"getMinecraft",world);
        Object api=call(cls("appeng.api.AEApi"),"instance");
        list=call(call(api,"storage"),"createItemList");
        itemType=get(cls("appeng.util.item.AEItemStackType"),"ITEM_STACK_TYPE");
        monitor=proxy(new String[]{"appeng.api.storage.IMEMonitor"},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a)throws Throwable{
                String n=m.getName();
                if(n.equals("getStorageList"))return list;
                if(n.equals("getAvailableItems")){for(Object s:(Iterable<?>)list)call(a[0],"addStorage",call(s,"copy"));return a[0];}
                if(n.equals("extractItems")){
                    Object stored=call(list,"findPrecise",a[0]);if(stored==null)return null;
                    long take=Math.min(((Number)call(stored,"getStackSize")).longValue(),((Number)call(a[0],"getStackSize")).longValue());
                    if(take<=0)return null;
                    Object out=call(stored,"copy");call(out,"setStackSize",take);
                    if(a[1].toString().equals("MODULATE"))call(stored,"setStackSize",((Number)call(stored,"getStackSize")).longValue()-take);
                    return out;
                }
                if(n.equals("injectItems")){
                    long requested=((Number)call(a[0],"getStackSize")).longValue(), accepted=Math.min(requested,insertCapacity);
                    if(a[1].toString().equals("MODULATE")&&accepted>0){Object stored=call(a[0],"copy");call(stored,"setStackSize",accepted);call(list,"addStorage",stored);insertCapacity-=accepted;}
                    if(accepted==requested)return null;
                    Object rejected=call(a[0],"copy");call(rejected,"setStackSize",requested-accepted);return rejected;
                }
                if(n.equals("getAccess"))return Enum.valueOf((Class)cls("appeng.api.config.AccessRestriction"),"READ_WRITE");
                return null;
            }
        });
        final Object energy=proxy(new String[]{"appeng.api.networking.energy.IEnergyGrid"},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a){if(m.getName().equals("isNetworkPowered"))return powered;if(m.getName().equals("extractAEPower"))return a[0];return null;}
        });
        final Object security=proxy(new String[]{"appeng.api.networking.security.ISecurityGrid"},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a){if(m.getName().equals("hasPermission"))return allowed&&(!a[1].toString().equals("INJECT")||injectAllowed);return null;}
        });
        final Object storage=proxy(new String[]{"appeng.api.networking.storage.IStorageGrid"},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a){if(m.getName().equals("getItemInventory"))return monitor;return null;}
        });
        final Object grid=proxy(new String[]{"appeng.api.networking.IGrid"},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a){if(m.getName().equals("getCache")){String n=((Class<?>)a[0]).getName();if(n.endsWith("IEnergyGrid"))return energy;if(n.endsWith("ISecurityGrid"))return security;if(n.endsWith("IStorageGrid"))return storage;}return null;}
        });
        final Object node=proxy(new String[]{"appeng.api.networking.IGridNode"},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a){if(m.getName().equals("getGrid"))return grid;if(m.getName().equals("isActive"))return powered;return null;}
        });
        final Object crafting=cls("net.minecraft.inventory.InventoryBasic").getConstructor(String.class,boolean.class,int.class).newInstance("craft",true,9);
        final Object views=cls("net.minecraft.inventory.InventoryBasic").getConstructor(String.class,boolean.class,int.class).newInstance("views",true,5);
        final Object cm=cls("appeng.util.ConfigManager").getConstructor(cls("appeng.util.IConfigManagerHost")).newInstance(new Object[]{null});
        for(String[] setting:new String[][]{{"SORT_BY","SortOrder","NAME"},{"VIEW_MODE","ViewItems","ALL"},{"SORT_DIRECTION","SortDir","ASCENDING"}}){
            call(cm,"registerSetting",Enum.valueOf((Class)cls("appeng.api.config.Settings"),setting[0]),Enum.valueOf((Class)cls("appeng.api.config."+setting[1]),setting[2]));
        }
        Object host=proxy(new String[]{"appeng.api.parts.ICraftingTerminal","appeng.api.networking.IGridHost","appeng.api.implementations.guiobjects.IGuiItemObject","appeng.api.networking.security.IActionHost"},new InvocationHandler(){
            public Object invoke(Object p,Method m,Object[] a){String n=m.getName();
                if(n.equals("getConfigManager"))return cm;
                if(n.equals("getMEMonitor"))return a[0]==itemType?monitor:null;
                if(n.equals("getItemInventory"))return monitor;
                if(n.equals("getGridNode")||n.equals("getActionableNode"))return node;
                if(n.equals("getInventoryByName"))return crafting;
                if(n.equals("getViewCellStorage"))return views;
                return null;
            }
        });
        Object inventory=get(player,"field_71071_by","inventory");
        container=cls("appeng.container.implementations.ContainerCraftingTerm").getConstructor(cls("net.minecraft.entity.player.InventoryPlayer"),cls("appeng.api.storage.ITerminalHost")).newInstance(inventory,host);
        // The controlled energy mock is independent of actual AE channel topology.
        Reflect.set(container,energy,"powerSrc");Reflect.set(container,7,"field_75152_c","windowId");Reflect.set(player,container,"field_71070_bA","openContainer");
    }
    Object recipe(Object output,Object[] inputs)throws Exception{
        List<Object> ingredients=new ArrayList<Object>();for(Object input:inputs)ingredients.add(stack(input,1));
        Object r=cls("net.minecraft.item.crafting.ShapelessRecipes").getConstructor(cls("net.minecraft.item.ItemStack"),List.class).newInstance(output,ingredients);
        Object cm=call(cls("net.minecraft.item.crafting.CraftingManager"),"func_77594_a|getInstance");
        ((List<Object>)call(cm,"func_77592_b|getRecipeList")).add(0,r);added.add(r);return r;
    }
    BridgeNetwork.Request request(int amount,Object result,Object... inputs)throws Exception{
        Object root=InventorySnapshot.tag();
        for(int i=0;i<inputs.length;i++){
            Object tags=cls("net.minecraft.nbt.NBTTagList").newInstance();
            Object tag=call(stack(inputs[i],1),"func_77955_b|writeToNBT",InventorySnapshot.tag());
            // Native PacketNEIRecipe reads this Count as a short.
            Method shortSetter=tag.getClass().getMethod("func_74777_a",String.class,short.class);shortSetter.invoke(tag,"Count",(short)1);
            call(tags,"func_74742_a|appendTag",tag);call(root,"func_74782_a|setTag","#"+i,tags);
        }
        return new BridgeNetwork.Request(1,7,amount,InventorySnapshot.compress(root),InventorySnapshot.writeStack(result));
    }
    long playerAmount(Object item)throws Exception{
        Object inventory=get(player,"field_71071_by","inventory");long n=0;
        for(int i=0;i<36;i++){Object s=call(inventory,"func_70301_a|getStackInSlot",i);if(s!=null&&call(s,"func_77973_b|getItem")==item)n+=((Number)get(s,"field_77994_a","stackSize")).intValue();}return n;
    }
    void run()throws Exception{
        setup();
        Object star=item("field_151156_bN"), quartz=item("field_151128_bU"), emerald=item("field_151166_bC"), diamond=item("field_151045_i");
        recipe(stack(quartz,4),new Object[]{star});recipe(stack(emerald,2),new Object[]{quartz,quartz});recipe(stack(diamond,1),new Object[]{emerald,emerald});
        put(star,50);
        BridgeNetwork.Result a=ServerCrafting.execute(request(3,stack(quartz,4),star),player);
        evidence.append("FIRST status=").append(a.status).append(" completed=").append(a.completed).append(" playerQuartz=").append(playerAmount(quartz)).append('\n');
        check(a.status==0&&a.completed==3&&playerAmount(quartz)==12,"3 crafts produce exactly 12, no shift overcraft");
        BridgeNetwork.Result b=ServerCrafting.execute(request(6,stack(emerald,2),quartz,quartz),player);
        check(b.status==0&&b.completed==6&&playerAmount(emerald)==12,"chain stage 2 consumes real player intermediates");
        BridgeNetwork.Result c=ServerCrafting.execute(request(6,stack(diamond,1),emerald,emerald),player);
        check(c.status==0&&c.completed==6&&playerAmount(diamond)==6,"chain stage 3 produces six final items");
        check(emptyMatrix(),"completed chain returns every remaining ingredient to ME");
        check(amount(star)+playerAmount(star)+matrixAmount(star)==47,"exact upstream consumption, including real matrix remainder");
        // Unsupported or stale requests cannot use an old result already in the grid.
        BridgeNetwork.Request wrong=request(1,stack(quartz,4),star);wrong.window=99;
        check(ServerCrafting.execute(wrong,player).completed==0,"stale window rejected");
        allowed=false;check(ServerCrafting.execute(request(1,stack(quartz,4),star),player).status==2,"security permission enforced");allowed=true;
        powered=false;check(ServerCrafting.execute(request(1,stack(quartz,4),star),player).status==2,"unpowered network rejected");powered=true;
        check(ServerCrafting.execute(request(0,stack(quartz,4),star),player).status==3,"invalid craft count rejected");
        BridgeNetwork.Result mismatch=ServerCrafting.execute(request(1,stack(diamond,1),star),player);
        check(mismatch.status==0&&mismatch.completed==0,"wrong recipe output never crafted");
        BridgeNetwork.Result exhaust=ServerCrafting.execute(request(64,stack(quartz,4),star),player);
        check(exhaust.status==0&&exhaust.completed==47,"material exhaustion reports only 47 actual crafts");
        check(ServerCrafting.execute(request(1,stack(quartz,4),star),player).completed==0,"empty input stops without invented production");
        check(ServerCrafting.cap(16)==16,"craft cap removed after requests, manual shift behavior preserved");
        // Read the long quantities directly on a dedicated server (NEI client classes are intentionally absent).
        Object entries=call(InventorySnapshot.expand(exhaust.snapshot),"func_150295_c|getTagList","items",10);
        long observed=0;int n=((Number)call(entries,"func_74745_c|tagCount")).intValue();
        for(int i=0;i<n;i++){Object e=call(entries,"func_150305_b|getCompoundTagAt",i);Object s=InventorySnapshot.readStack(e);if(call(s,"func_77973_b|getItem")==quartz)observed+=((Number)call(e,"func_74763_f|getLong","bridgeQuantity")).longValue();}
        check(observed==188,"authoritative snapshot contains real final inventory, excludes preview output");
        Object decoded=InventorySnapshot.decode(exhaust.snapshot);
        check(((Number)call(decoded,"getOrDefault",stack(quartz,1),0L)).longValue()==188,"real NEI ItemStackAmount decodes authoritative quantities");
        // Values beyond ItemStack's int/byte counts and NBT-distinct variants stay separate.
        Object huge=ae(stack(star,1));call(huge,"setStackSize",5_000_000_000L);call(list,"addStorage",huge);
        Object tagged=stack(star,1), tag=InventorySnapshot.tag();call(tag,"func_74778_a|setString","auditVariant","B");call(tagged,"func_77982_d|setTagCompound",tag);call(list,"addStorage",ae(tagged));
        decoded=InventorySnapshot.decode(InventorySnapshot.capture(container,player));
        check(((Number)call(decoded,"getOrDefault",stack(star,1),0L)).longValue()==5_000_000_000L,"ME long quantities retained");
        check(((Number)call(decoded,"getOrDefault",tagged,0L)).longValue()==1,"NBT-distinct ME items never merged");
        // Remove only this test's synthetic stocks before the full-inventory test.
        call(list,"resetStatus");
        checkReceive();
        Object inv=get(player,"field_71071_by","inventory");for(int i=0;i<36;i++)call(inv,"func_70299_a|setInventorySlotContents",i,stack(diamond,64));put(star,20);
        check(ServerCrafting.execute(request(1,stack(quartz,4),star),player).completed==0,"full player inventory stops without dropping crafted items");
        check(emptyMatrix()&&amount(star)==20,"failed full-inventory craft returns prefetched material without consumption");
        polishTests(star,quartz,diamond);
        intermediateTests(star,quartz,emerald,diamond);
        turbineTest(star,diamond);
        benchmarkSnapshot(star);
    }
    void clearPlayer()throws Exception{Object inv=get(player,"field_71071_by","inventory");for(int i=0;i<36;i++)call(inv,"func_70299_a|setInventorySlotContents",i,null);}
    boolean emptyMatrix()throws Exception{Object matrix=call(container,"getInventoryByName","crafting");for(int i=0;i<9;i++)if(call(matrix,"func_70301_a|getStackInSlot",i)!=null)return false;return true;}
    int occupied(Object item)throws Exception{Object inv=get(player,"field_71071_by","inventory");int n=0;for(int i=0;i<36;i++){Object s=call(inv,"func_70301_a|getStackInSlot",i);if(s!=null&&call(s,"func_77973_b|getItem")==item)n++;}return n;}
    public static class Goal { public final Object itemStack; public Goal(Object stack){itemStack=stack;} }
    public static class Plan { public final List<Goal> initialItems=new ArrayList<Goal>(); public Plan(Object... stacks){for(Object s:stacks)initialItems.add(new Goal(s));} }
    BridgeNetwork.Request intermediate(int count,Object result,Object... inputs)throws Exception{
        BridgeNetwork.Request req=request(count,result,inputs);req.operation=2;return req;
    }
    void intermediateTests(Object star,Object quartz,Object emerald,Object diamond)throws Exception{
        Object finalStack=stack(diamond,1), middle=stack(quartz,4);
        check(AE2NEICraftingBridge.craftOperation(finalStack,new Plan(finalStack))==1
            &&AE2NEICraftingBridge.craftOperation(middle,new Plan(finalStack))==2,"requested output retained; intermediate classified from initial goals");
        check(AE2NEICraftingBridge.craftOperation(middle,new Plan(finalStack,middle))==1
            &&AE2NEICraftingBridge.craftOperation(middle,new Plan())==1
            &&AE2NEICraftingBridge.craftOperation(middle,null)==1,"multiple explicit goals and unknown goal lists preserve requested outputs");
        Object variant=stack(quartz,3),tag=InventorySnapshot.tag();call(tag,"func_74778_a|setString","auditVariant","different");call(variant,"func_77982_d|setTagCompound",tag);
        check(AE2NEICraftingBridge.craftOperation(middle,new Plan(variant))==2,"goal classification distinguishes exact NBT variants");
        clearPlayer();call(list,"resetStatus");insertCapacity=Long.MAX_VALUE;put(star,10);
        BridgeNetwork.Result a=ServerCrafting.execute(intermediate(1,middle,star),player);
        check(a.completed==1&&playerAmount(quartz)==0&&amount(quartz)==4,"four intermediate outputs go to ME with exact native completion count");
        BridgeNetwork.Result b=ServerCrafting.execute(intermediate(1,stack(emerald,2),quartz,quartz),player);
        check(b.completed==1&&playerAmount(emerald)==0&&amount(emerald)==2&&amount(quartz)==2,"next stage consumes real ME intermediates and retains batch surplus in ME");
        BridgeNetwork.Result c=ServerCrafting.execute(request(1,finalStack,emerald,emerald),player);
        check(c.completed==1&&playerAmount(diamond)==1&&playerAmount(quartz)==0&&playerAmount(emerald)==0
            &&amount(quartz)==2&&amount(emerald)==0&&amount(star)==9&&emptyMatrix(),"odd-sized chain leaves only final output in player; real surplus and exact raw consumption in ME");
        clearPlayer();call(list,"resetStatus");put(star,10);
        Object inv=get(player,"field_71071_by","inventory");call(inv,"func_70299_a|setInventorySlotContents",5,stack(quartz,7));call(inv,"func_70299_a|setInventorySlotContents",13,variant);
        a=ServerCrafting.execute(intermediate(2,middle,star),player);
        check(a.completed==2&&playerAmount(quartz)==10&&amount(quartz)==8&&call(inv,"func_70301_a|getStackInSlot",13)==variant,"routing transfers only new quantity; original items and NBT variants retained");
        Object decoded=InventorySnapshot.decode(a.snapshot);
        check(((Number)call(decoded,"getOrDefault",stack(quartz,1),0L)).longValue()==15,"authoritative snapshot counts deposited intermediate and original player quantity once");
        clearPlayer();call(list,"resetStatus");put(star,10);insertCapacity=2;
        a=ServerCrafting.execute(intermediate(2,middle,star),player);
        check(a.completed==2&&amount(quartz)==2&&playerAmount(quartz)==6,"partial insertion preserves exactly six rejected new outputs in player");
        clearPlayer();call(list,"resetStatus");put(star,10);insertCapacity=0;
        a=ServerCrafting.execute(intermediate(1,middle,star),player);
        check(a.completed==1&&amount(quartz)==0&&playerAmount(quartz)==4,"full ME retains every new intermediate in player without drops");
        clearPlayer();call(list,"resetStatus");put(star,10);insertCapacity=Long.MAX_VALUE;injectAllowed=false;
        a=ServerCrafting.execute(intermediate(1,middle,star),player);
        check(a.completed==1&&amount(quartz)==0&&playerAmount(quartz)==4,"missing INJECT permission does not move intermediate outputs");injectAllowed=true;
        powered=false;ServerCrafting.depositCraftedOutput(container,player,middle,4);
        check(amount(quartz)==0&&playerAmount(quartz)==4,"unpowered network leaves all routed quantity in player");powered=true;
        clearPlayer();call(list,"resetStatus");put(star,30);
        ServerCrafting.Job job=new ServerCrafting.Job(intermediate(10,middle,star),player);
        check(job.advance(4)==null&&amount(quartz)==16&&playerAmount(quartz)==0,"first intermediate slice deposits only four crafts");
        check(job.advance(4)==null&&amount(quartz)==32,"deposited prior slice does not corrupt next native completion count");
        a=job.advance(4);check(a.completed==10&&amount(quartz)==40&&playerAmount(quartz)==0&&emptyMatrix(),"sliced intermediate request reports ten crafts while keeping all output in ME");
        job=new ServerCrafting.Job(intermediate(10,middle,star),player);job.advance(4);a=job.abort();
        check(a.status==5&&a.completed==4&&amount(quartz)==56&&playerAmount(quartz)==0&&emptyMatrix(),"cancelled intermediate request retains all confirmed deposited outputs");
    }
    void polishTests(Object star,Object quartz,Object diamond)throws Exception{
        clearPlayer();call(list,"resetStatus");insertCapacity=Long.MAX_VALUE;put(star,30);
        Object inv=get(player,"field_71071_by","inventory"), untouched=stack(diamond,2);
        call(inv,"func_70299_a|setInventorySlotContents",0,untouched);
        call(inv,"func_70299_a|setInventorySlotContents",5,stack(quartz,1));call(inv,"func_70299_a|setInventorySlotContents",10,stack(quartz,1));
        BridgeNetwork.Result result=ServerCrafting.execute(request(2,stack(quartz,4),star),player);
        check(result.status==0&&result.completed==2&&playerAmount(quartz)==10&&occupied(quartz)==1,"outputs merge across intermediate-created gaps into one stack");
        check(call(inv,"func_70301_a|getStackInSlot",0)==untouched&&emptyMatrix(),"unrelated slots preserved and grid empty after output compaction");
        clearPlayer();call(inv,"func_70299_a|setInventorySlotContents",5,stack(quartz,63));call(inv,"func_70299_a|setInventorySlotContents",12,stack(quartz,5));
        Object variant=stack(quartz,3),tag=InventorySnapshot.tag();call(tag,"func_74778_a|setString","auditVariant","different");call(variant,"func_77982_d|setTagCompound",tag);call(inv,"func_70299_a|setInventorySlotContents",13,variant);
        ServerCrafting.compactOutput(player,stack(quartz,1));
        check(((Number)get(call(inv,"func_70301_a|getStackInSlot",5),"field_77994_a","stackSize")).intValue()==64&&((Number)get(call(inv,"func_70301_a|getStackInSlot",12),"field_77994_a","stackSize")).intValue()==4&&call(inv,"func_70301_a|getStackInSlot",13)==variant,"max stack limit and NBT variants preserved");
        Object sword=item("field_151040_l");call(inv,"func_70299_a|setInventorySlotContents",7,stack(sword,1));call(inv,"func_70299_a|setInventorySlotContents",8,stack(sword,1));
        ServerCrafting.compactOutput(player,stack(sword,1));check(occupied(sword)==2,"unstackable tools remain separate");
        clearPlayer();
        for(int i=0;i<36;i++)call(inv,"func_70299_a|setInventorySlotContents",i,stack(diamond,64));
        call(inv,"func_70299_a|setInventorySlotContents",0,stack(quartz,60));
        result=ServerCrafting.execute(request(8,stack(quartz,4),star),player);
        check(result.status==0&&result.completed==1&&playerAmount(quartz)==64&&emptyMatrix(),
            "near-full player inventory accepts one complete craft instead of rejecting a four-craft batch");
        clearPlayer();
        ServerCrafting.Job job=new ServerCrafting.Job(request(10,stack(quartz,4),star),player);
        check(job.advance(4)==null&&playerAmount(quartz)==16&&ServerCrafting.cap(16)==16,"one tick slice makes at most four crafts and releases thread cap");
        check(job.advance(4)==null&&playerAmount(quartz)==32,"same request continues on next tick with no duplicate operation");
        result=job.advance(4);check(result!=null&&result.completed==10&&playerAmount(quartz)==40&&emptyMatrix(),"sliced request reports cumulative exact count and cleans once");
        job=new ServerCrafting.Job(request(10,stack(quartz,4),star),player);check(job.advance(4)==null,"partial request can be suspended");
        result=job.abort();check(result.status==5&&result.completed==4&&emptyMatrix(),"expired request retains confirmed output and returns matrix without retry");
        Object matrix=call(container,"getInventoryByName","crafting");clearPlayer();injectAllowed=false;
        call(matrix,"func_70299_a|setInventorySlotContents",0,stack(star,3));long before=amount(star);
        ServerCrafting.returnRemainders(container,player);
        check(emptyMatrix()&&playerAmount(star)==3&&amount(star)==before,"no INJECT permission returns remainder to player without ME insertion");injectAllowed=true;
        for(int i=0;i<36;i++)call(inv,"func_70299_a|setInventorySlotContents",i,stack(diamond,64));
        insertCapacity=0;call(matrix,"func_70299_a|setInventorySlotContents",0,stack(star,5));before=amount(star);
        ServerCrafting.returnRemainders(container,player);check(matrixAmount(star)==5&&amount(star)==before,"full ME and player preserve all remainder in grid without drops");
        insertCapacity=2;ServerCrafting.returnRemainders(container,player);check(matrixAmount(star)==3&&amount(star)==before+2,"partial ME insertion retains exact rejected remainder");
        clearPlayer();insertCapacity=Long.MAX_VALUE;ServerCrafting.returnRemainders(container,player);check(emptyMatrix()&&amount(star)==before+5,"retained remainder can be recovered later without loss");
    }
    void benchmarkSnapshot(Object star)throws Exception{
        call(list,"resetStatus");
        for(int i=0;i<2048;i++){Object stack=stack(star,1),tag=InventorySnapshot.tag();call(tag,"func_74778_a|setString","auditVariant",String.valueOf(i));call(stack,"func_77982_d|setTagCompound",tag);call(list,"addStorage",ae(stack));}
        final ClassLoader parent=getClass().getClassLoader();
        java.net.URLClassLoader old=new java.net.URLClassLoader(new java.net.URL[]{Paths.get(System.getProperty("ae2nei.auditBaseline")).toUri().toURL()},parent){
            protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.equals("ae2nei.bridge.Reflect")||name.equals("ae2nei.bridge.InventorySnapshot")){
                    synchronized(getClassLoadingLock(name)){Class<?> loaded=findLoadedClass(name);if(loaded==null)loaded=findClass(name);if(resolve)resolveClass(loaded);return loaded;}
                }return super.loadClass(name,resolve);
            }
        };
        try{
            Method capture=old.loadClass("ae2nei.bridge.InventorySnapshot").getMethod("capture",Object.class,Object.class);
            capture.invoke(null,container,player);InventorySnapshot.capture(container,player);
            long[] prior=new long[3],current=new long[3];byte[] a=null,b=null;
            for(int i=0;i<3;i++){long start=System.nanoTime();a=(byte[])capture.invoke(null,container,player);prior[i]=System.nanoTime()-start;start=System.nanoTime();b=InventorySnapshot.capture(container,player);current[i]=System.nanoTime()-start;}
            Arrays.sort(prior);Arrays.sort(current);
            check(Arrays.equals(a,b),"optimized 2048-entry snapshot remains byte-identical to 1.1.0");
            evidence.append("BENCH snapshot entries=2048 baselineMedianMs=").append(prior[1]/1_000_000.0).append(" currentMedianMs=").append(current[1]/1_000_000.0).append('\n');
        }finally{old.close();}
    }
    void turbineTest(Object star,Object diamond)throws Exception{
        clearPlayer();call(list,"resetStatus");put(star,10);put(diamond,10);
        Object registered=get(cls("gregtech.api.enums.ItemList"),"Generator_Steam_Turbine_LV");
        Object turbine=call(registered,"get",1L,new Object[0]);
        // A unique input pair avoids the native recipe cache from the earlier star->quartz test.
        recipe(turbine,new Object[]{star,diamond});
        Object inv=get(player,"field_71071_by","inventory");
        call(inv,"func_70299_a|setInventorySlotContents",0,stack(diamond,1));
        BridgeNetwork.Result first=ServerCrafting.execute(request(1,turbine,star,diamond),player);
        call(inv,"func_70299_a|setInventorySlotContents",0,null);
        BridgeNetwork.Result second=ServerCrafting.execute(request(1,turbine,star,diamond),player);
        Object merged=call(inv,"func_70301_a|getStackInSlot",0);
        evidence.append("TURBINE firstStatus=").append(first.status).append(" firstCount=").append(first.completed).append(" secondStatus=").append(second.status)
            .append(" secondCount=").append(second.completed).append(" maxStack=").append(call(turbine,"func_77976_d|getMaxStackSize"))
            .append(" expectedNBT=").append(call(turbine,"func_77955_b|writeToNBT",InventorySnapshot.tag())).append(" slot0=")
            .append(merged==null?"null":call(merged,"func_77955_b|writeToNBT",InventorySnapshot.tag())).append(" matrixEmpty=").append(emptyMatrix()).append('\n');
        check(first.completed==1&&second.completed==1&&merged!=null&&((Number)get(merged,"field_77994_a","stackSize")).intValue()==2
            &&call(cls("appeng.util.Platform"),"isSameItemPrecise",merged,turbine).equals(true)&&emptyMatrix(),
            "two actual GT basic steam turbines merge despite a newly freed earlier slot; grid empty");
    }
    void checkReceive()throws Exception{
        Object unsafe=get(cls("sun.misc.Unsafe"),"theUnsafe");
        Object handler=call(unsafe,"allocateInstance",cls("net.minecraft.network.NetHandlerPlayServer"));
        Reflect.set(handler,player,"field_147369_b","playerEntity");
        Constructor<?> constructor=cpw.mods.fml.common.network.simpleimpl.MessageContext.class.getDeclaredConstructors()[0];constructor.setAccessible(true);
        cpw.mods.fml.common.network.simpleimpl.MessageContext context=(cpw.mods.fml.common.network.simpleimpl.MessageContext)constructor.newInstance(handler,cpw.mods.fml.relauncher.Side.SERVER);
        BridgeNetwork.Request req=new BridgeNetwork.Request(0,7,0,new byte[0],new byte[0]);req.id=100;
        new BridgeNetwork.RequestHandler().onMessage(req,context);
        Map<Object,Object> queue=(Map<Object,Object>)get(BridgeNetwork.class,"SERVER");
        check(queue.containsKey(player),"dedicated-server request routing uses authenticated sender");queue.remove(player);
        new BridgeNetwork.RequestHandler().onMessage(req,context);
        check(!queue.containsKey(player),"duplicate request IDs cannot craft twice");
        clearPlayer();call(list,"resetStatus");Object star=item("field_151156_bN"),quartz=item("field_151128_bU");put(star,20);
        req=request(10,stack(quartz,4),star);req.id=101;
        new BridgeNetwork.RequestHandler().onMessage(req,context);
        Object task=queue.get(player),job=get(task,"job");
        check(call(job,"advance",4)==null&&playerAmount(quartz)==16,"network task executes its first native slice");
        new BridgeNetwork.CancelHandler().onMessage(new BridgeNetwork.Cancel(101),context);
        check(Boolean.TRUE.equals(get(task,"cancelled")),"cancel handler marks authenticated native task");
        BridgeNetwork.Request next=new BridgeNetwork.Request(0,7,0,new byte[0],new byte[0]);next.id=102;
        check(new BridgeNetwork.RequestHandler().onMessage(next,context)==null&&get(task,"next")!=null,
            "new request queues behind cancellation instead of being silently lost");
        new BridgeNetwork().serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        check(playerAmount(quartz)==16&&emptyMatrix()&&queue.get(player)!=task,
            "cancelled native task stops consuming on next tick and cleans before successor");
        new BridgeNetwork().serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START));
        check(!queue.containsKey(player)&&playerAmount(quartz)==16,"successor snapshot completes and releases player queue");
        clearPlayer();call(list,"resetStatus");
    }
    long matrixAmount(Object item)throws Exception{
        Object inv=call(container,"getInventoryByName","crafting");long amount=0;for(int i=0;i<9;i++){Object s=call(inv,"func_70301_a|getStackInSlot",i);if(s!=null&&call(s,"func_77973_b|getItem")==item)amount+=((Number)get(s,"field_77994_a","stackSize")).intValue();}return amount;
    }
}
