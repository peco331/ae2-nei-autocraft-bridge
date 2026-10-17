package ae2nei.bridge;

import ae2nei.core.AE2NEITransformer;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.RestartableTask;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.chain.RecipeChainMath;
import codechicken.nei.recipe.chain.RecipeChainIterator;
import net.minecraft.item.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Executes upstream planning bytecode and the real transformed batch loop without Minecraft startup. */
public class HeadlessChainVerify {
    static int checks;
    static final String SELF="ae2nei/bridge/HeadlessChainVerify";
    static final String WORKER="ae2nei/bridge/HeadlessBatchLoop";
    static final List<Integer> requests=new ArrayList<Integer>();
    static int[] responses;static int responseIndex,produced;static boolean acknowledge;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    public static boolean craft(Object ref,Object gui,int requested){
        requests.add(requested);if(requests.size()>100)throw new AssertionError("batch did not terminate");
        int actual=Math.min(requested,responseIndex<responses.length?responses[responseIndex++]:0);produced+=actual;
        return acknowledge?AE2NEICraftingBridge.recordCompleted(actual,requested):actual>0;
    }
    static Object worker(byte[] bytes)throws Exception{
        ClassNode source=new ClassNode();new ClassReader(bytes).accept(source,0);
        MethodNode execute=null;for(MethodNode m:(List<MethodNode>)source.methods)if(m.name.equals("execute"))execute=m;
        JumpInsnNode end=null;AbstractInsnNode start=null;
        for(AbstractInsnNode n=execute.instructions.getFirst();n!=null;n=n.getNext()){
            if(n.getOpcode()==Opcodes.IFLE&&n.getPrevious().getOpcode()==Opcodes.LCMP){
                end=(JumpInsnNode)n;start=n.getPrevious().getPrevious().getPrevious();break;
            }
        }
        if(start==null)throw new AssertionError("native batch loop not found");
        while(start.getPrevious()!=null&&start.getPrevious().getOpcode()<0)start=start.getPrevious();
        int multiplier=((VarInsnNode)firstOpcode(start)).var;
        Map<LabelNode,LabelNode> labels=new HashMap<LabelNode,LabelNode>();
        for(AbstractInsnNode n=execute.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof LabelNode)labels.put((LabelNode)n,new LabelNode());
        ClassNode target=new ClassNode();target.version=Opcodes.V1_8;target.access=Opcodes.ACC_PUBLIC;target.name=WORKER;target.superName="java/lang/Object";
        MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));init.instructions.add(new InsnNode(Opcodes.RETURN));target.methods.add(init);
        MethodNode interrupted=new MethodNode(Opcodes.ACC_PRIVATE,"interrupted","(Ljava/lang/Object;)Z",null,null);interrupted.instructions.add(new InsnNode(Opcodes.ICONST_0));interrupted.instructions.add(new InsnNode(Opcodes.IRETURN));target.methods.add(interrupted);
        MethodNode run=new MethodNode(Opcodes.ACC_PUBLIC,"run","(Ljava/lang/Object;Ljava/lang/Object;J)J",null,null);
        for(AbstractInsnNode n=start;n!=end.label;n=n.getNext()){
            if(n instanceof FrameNode||n instanceof LineNumberNode)continue;
            AbstractInsnNode copy=n.clone(labels);
            if(copy instanceof VarInsnNode){VarInsnNode v=(VarInsnNode)copy;if(v.var==multiplier)v.var=3;else if(v.var==1)v.var=2;else if(v.var!=0)v.var=1;}
            if(copy instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)copy;
                if(m.name.equals("interrupted")){m.owner=WORKER;m.desc="(Ljava/lang/Object;)Z";}
                if(m.name.equals("craft")){m.setOpcode(Opcodes.INVOKESTATIC);m.owner=SELF;m.name="craft";m.desc="(Ljava/lang/Object;Ljava/lang/Object;I)Z";m.itf=false;}
            }
            run.instructions.add(copy);
        }
        run.instructions.add(labels.get(end.label));run.instructions.add(new VarInsnNode(Opcodes.LLOAD,3));run.instructions.add(new InsnNode(Opcodes.LRETURN));target.methods.add(run);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);target.accept(writer);final byte[] out=writer.toByteArray();
        Class<?> c=new ClassLoader(HeadlessChainVerify.class.getClassLoader()){Class<?> load(){return defineClass(WORKER.replace('/','.'),out,0,out.length);}}.load();
        return c.newInstance();
    }
    static AbstractInsnNode firstOpcode(AbstractInsnNode n){while(n.getOpcode()<0)n=n.getNext();return n;}
    static long replay(Object worker,int wanted,boolean ack,int... actual)throws Exception{
        AE2NEICraftingBridge.beginProcessing();requests.clear();responses=actual;responseIndex=0;produced=0;acknowledge=ack;
        return (Long)worker.getClass().getMethod("run",Object.class,Object.class,long.class).invoke(worker,new Object(),new Object(),(long)wanted);
    }
    static BookmarkItem bookmark(Item item,RecipeId recipe,BookmarkItemType type,long factor,long multiplier){return BookmarkItem.builder(2,new ItemStack(item,1)).recipeId(recipe).type(type).factor(factor).multiplier(multiplier).build();}
    static void nativePlanner()throws Exception{
        Item raw=new Item("raw"),middle=new Item("middle"),goal=new Item("goal");
        RecipeId intermediate=new RecipeId("make-middle",new ItemStack(middle,1)),target=new RecipeId("make-goal",new ItemStack(goal,1));
        List<BookmarkItem> bookmarks=Arrays.asList(bookmark(goal,target,BookmarkItemType.RESULT,1,4),bookmark(middle,target,BookmarkItemType.INGREDIENT,1,4),bookmark(middle,intermediate,BookmarkItemType.RESULT,1,1),bookmark(raw,intermediate,BookmarkItemType.INGREDIENT,1,1));
        RecipeChainMath math=RecipeChainMath.of(bookmarks,Collections.<RecipeId>emptySet());math.createMasterRoot();
        check(AE2NEICraftingBridge.goalItems(math).size()==1&&AE2NEICraftingBridge.goalStacks(math).get(0) instanceof ItemStack,"actual NEI master root contains one final target");
        ItemStackAmount inventory=new ItemStackAmount();inventory.add(new ItemStack(goal,1),2L);inventory.add(new ItemStack(raw,1),100L);
        RecipeChainIterator iterator=new RecipeChainIterator(math,Collections.<BookmarkItem>emptyList());iterator.updateInventory(inventory);
        Long targetCount=null,middleCount=null;
        while(iterator.hasNext()){Map<RecipeId,Long> next=iterator.next();if(next.containsKey(target))targetCount=next.get(target);if(next.containsKey(intermediate))middleCount=next.get(intermediate);}
        check(Long.valueOf(2).equals(targetCount)&&Long.valueOf(2).equals(middleCount),"actual NEI iterator plans two missing final items and two intermediates for target four with two already present");
        inventory=new ItemStackAmount();inventory.add(new ItemStack(goal,1),4L);iterator=new RecipeChainIterator(math,Collections.<BookmarkItem>emptyList());iterator.updateInventory(inventory);boolean work=false;
        while(iterator.hasNext())work|=!iterator.next().isEmpty();check(!work,"actual NEI iterator schedules no crafting once all four real goals are present");
    }
    static void craftItemsRetrieval()throws Exception{
        Item raw=new Item("raw"),plate=new Item("plate"),turbine=new Item("turbine");
        RecipeId plates=new RecipeId("make-plate",new ItemStack(plate,1)),turbines=new RecipeId("make-turbine",new ItemStack(turbine,1));
        List<BookmarkItem> bookmarks=Arrays.asList(bookmark(plate,plates,BookmarkItemType.RESULT,1,4),bookmark(raw,plates,BookmarkItemType.INGREDIENT,1,4),bookmark(turbine,turbines,BookmarkItemType.RESULT,1,2),bookmark(raw,turbines,BookmarkItemType.INGREDIENT,1,2));
        RecipeChainMath missing=RecipeChainMath.of(bookmarks,Collections.<RecipeId>emptySet());missing.createMasterRoot();
        check(AE2NEICraftingBridge.retrievalGoals(missing).size()==2,"Craft Missing retrieves both actual master-root goals");
        RecipeChainMath math=RecipeChainMath.of(bookmarks,Collections.<RecipeId>emptySet());
        RecipeId root=math.createMasterRoot();check(AE2NEICraftingBridge.ignoreInventoryRoot(math,root)==root,"Craft Items hook returns NEI's own root id");
        // ShortcutInputHandler.autocraftingIgnoreInventory: 1900 plates already in ME + inventory, goal 4 -> next batch 1904.
        for(int i=0;i<math.recipeIngredients.size();i++){BookmarkItem item=math.recipeIngredients.get(i);
            if(root.equals(item.recipeId)&&item.itemStack.item==plate)math.recipeIngredients.set(i,BookmarkItem.builder(-1,new ItemStack(plate,1)).recipeId(root).type(BookmarkItemType.INGREDIENT).factor(1904).multiplier(1).build());}
        math.createMasterRoot(); // runProcessing repeats it after the raise
        List<Object> retrieve=AE2NEICraftingBridge.retrievalGoals(math);
        check(AE2NEICraftingBridge.goalItems(math).size()==2&&retrieve.size()==1&&((BookmarkItem)retrieve.get(0)).itemStack.item==turbine,"Craft Items retrieves an unmet goal but never the ME stock of a goal raised to its next batch");
    }
    static void retryAfterLookupFailure()throws Exception{
        final RecipeId id=new RecipeId("lookup-retry",new ItemStack(new Item("retry-output"),1));
        class Task extends RestartableTask {
            volatile CountDownLatch done=new CountDownLatch(1);int executions;
            Task(){super("NEI Bookmark AutoCraft Processing - headless regression");}
            public void execute(){AE2NEICraftingBridge.beginProcessing();try{AE2NEICraftingBridge.recipeReference(id,new appeng.client.gui.implementations.GuiCraftingTerm());executions++;}catch(Exception e){throw new RuntimeException(e);}}
            public void clearTasks(){super.clearTasks();done.countDown();}
        }
        Task task=new Task();PrintStream prior=System.err;ByteArrayOutputStream errors=new ByteArrayOutputStream();
        try{
            System.setErr(new PrintStream(errors,true,"UTF-8"));
            final int[] starts={0};RestartableTask broken=new RestartableTask("Headless upstream failure control"){public void execute(){starts[0]++;throw new IllegalStateException("unhandled upstream failure");}};
            broken.restart();Thread dead=(Thread)Reflect.get(broken,"thread");dead.join(2000);check(!dead.isAlive()&&Reflect.get(broken,"thread")==dead,"actual NEI RestartableTask retains its dead thread after an unhandled exception");
            broken.restart();check(starts[0]==1&&Reflect.get(broken,"thread")==dead,"native restart cannot revive that retained dead thread");
            RecipeHandlerRef.fail=true;boolean originalFailure=false;try{AE2NEICraftingBridge.recipeReference(id,new Object());}catch(Exception expected){originalFailure=true;}check(originalFailure,"ordinary crafting retains its own lookup exception behavior");
            task.restart();
            check(task.done.await(2,TimeUnit.SECONDS)&&task.executions==1,"lookup failure finishes the actual NEI task instead of leaving a dead thread registered");
            RecipeHandlerRef.fail=false;task.done=new CountDownLatch(1);task.restart();
            check(task.done.await(2,TimeUnit.SECONDS)&&task.executions==2,"the same actual NEI task can restart after the failed recipe lookup");
            check(errors.toString("UTF-8").contains("recipe lookup failed"),"the failed lookup remains diagnosable");
        }finally{RecipeHandlerRef.fail=false;System.setErr(prior);}
    }
    public static void main(String[] args)throws Exception{
        byte[] original;try(ZipFile jar=new ZipFile(args[0]);InputStream in=jar.getInputStream(jar.getEntry("codechicken/nei/recipe/AutoCraftingManager$1.class"));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);original=out.toByteArray();}
        Object before=worker(original);long remaining=replay(before,70,true,3,64,3);
        check(remaining<0&&produced==9&&requests.equals(Arrays.asList(64,6)),"original NEI loop misaccounts a partial acknowledgement: nine actual crafts exhaust a planned seventy in this pass");
        final byte[] transformed=new AE2NEITransformer().transform("codechicken.nei.recipe.AutoCraftingManager$1","codechicken.nei.recipe.AutoCraftingManager$1",original);
        Class<?> full=new ClassLoader(HeadlessChainVerify.class.getClassLoader()){Class<?> load(){return defineClass("codechicken.nei.recipe.AutoCraftingManager$1",transformed,0,transformed.length);}}.load();
        java.lang.reflect.Constructor<?> ctor=full.getDeclaredConstructor(String.class);ctor.setAccessible(true);check(ctor.newInstance("Headless bytecode verification")!=null,"the full transformed upstream worker passes JVM verification without starting a task");
        Object fixed=worker(transformed);
        remaining=replay(fixed,70,true,3,64,3);check(remaining==0&&produced==70&&requests.equals(Arrays.asList(64,64,3)),"transformed upstream loop consumes exact acknowledgements through partial and final batches");
        remaining=replay(fixed,2,true,1,1);check(remaining==0&&produced==2&&requests.equals(Arrays.asList(2,1)),"two missing goals finish across two partial acknowledgements");
        remaining=replay(fixed,4,true,0);check(remaining==4&&produced==0&&requests.size()==1,"zero progress stops the native loop without inventing completion or spinning");
        remaining=replay(fixed,70,false,64,6);check(remaining<0&&produced==70&&requests.equals(Arrays.asList(64,6)),"ordinary NEI overlay retains the original full-batch behavior");
        nativePlanner();craftItemsRetrieval();retryAfterLookupFailure();System.out.println("PASS: "+checks+" headless upstream worker/planner assertions; no game or server launched.");
    }
}
