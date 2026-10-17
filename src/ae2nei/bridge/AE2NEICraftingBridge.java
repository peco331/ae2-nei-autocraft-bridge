package ae2nei.bridge;

import java.util.List;
import java.util.concurrent.Callable;

/** NEI's worker waits for authoritative server results; the render thread never waits. */
public final class AE2NEICraftingBridge {
    private AE2NEICraftingBridge() {}
    private static final ThreadLocal<Boolean> HALTED = new ThreadLocal<Boolean>();
    private static final ThreadLocal<byte[]> SNAPSHOT = new ThreadLocal<byte[]>();
    private static final ThreadLocal<Object> PLAN = new ThreadLocal<Object>();
    private static final ThreadLocal<Boolean> NOTIFIED = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Boolean> PREPARED = new ThreadLocal<Boolean>();
    private static final ThreadLocal<Integer> COMPLETED = new ThreadLocal<Integer>();
    public static void beginProcessing() { HALTED.remove(); SNAPSHOT.remove(); PLAN.remove(); NOTIFIED.remove(); PREPARED.remove(); COMPLETED.remove(); }
    static byte[] takeSnapshot() { byte[] bytes = SNAPSHOT.get(); SNAPSHOT.remove(); return bytes; }
    static void rememberSnapshot(byte[] bytes) { SNAPSHOT.set(bytes); }
    public static boolean isCraftingTerm(Object gui) {
        try { return Reflect.is(gui, "appeng.client.gui.implementations.GuiCraftingTerm"); }
        catch (Exception e) { return false; }
    }
    private static boolean worker() {
        return Thread.currentThread().getName().startsWith("NEI Bookmark AutoCraft Processing");
    }
    public static boolean active(Object gui) {
        try {
            Object current = Reflect.call(Class.forName("codechicken.nei.NEIClientUtils"), "getGuiContainer");
            Object task = Reflect.get(Class.forName("codechicken.nei.recipe.AutoCraftingManager"), "task");
            return current == gui && !Boolean.TRUE.equals(Reflect.call(task, "interrupted"));
        } catch (Exception e) { return false; }
    }
    public static Object appendTerminalInventoryItems(final Object original, final Object gui) {
        if (!isCraftingTerm(gui)) return original;
        try {
            if (worker()) {
                if (Boolean.TRUE.equals(HALTED.get())) return emptyInventory();
                if (!Boolean.TRUE.equals(PREPARED.get())) {
                    Object plan = Reflect.get(Class.forName("codechicken.nei.recipe.AutoCraftingManager"), "math");
                    PLAN.set(plan);
                    final byte[] goals = GoalPreparation.encode(plan);
                    BridgeNetwork.Result prepared = BridgeNetwork.request(gui, new Callable<BridgeNetwork.Request>() {
                        public BridgeNetwork.Request call() throws Exception { return new BridgeNetwork.Request(3, window(gui), 0, goals, new byte[0]); }
                    });
                    if (prepared == null || prepared.status != 0) { HALTED.set(true); return emptyInventory(); }
                    PREPARED.set(true);
                    return InventorySnapshot.decode(prepared.snapshot);
                }
                byte[] observed = takeSnapshot();
                if (observed != null && active(gui)) return InventorySnapshot.decode(observed);
                BridgeNetwork.Result result = BridgeNetwork.request(gui, new Callable<BridgeNetwork.Request>() {
                    public BridgeNetwork.Request call() throws Exception {
                        return new BridgeNetwork.Request(0, window(gui), 0, new byte[0], new byte[0]);
                    }
                });
                if (result == null || result.status != 0) { HALTED.set(true); return emptyInventory(); }
                return InventorySnapshot.decode(result.snapshot);
            }
            Object repo = Reflect.get(gui, "repo");
            for (Object stack : (Iterable<?>) Reflect.get(repo, "list")) {
                if (!Reflect.is(stack, "appeng.api.storage.data.IAEItemStack")) continue;
                long count = ((Number) Reflect.call(stack, "getStackSize")).longValue();
                if (count > 0) Reflect.call(original, "add", Reflect.call(stack, "getItemStack"), count);
            }
        } catch (Exception e) { HALTED.set(true); report("inventory", e); return emptyInventory(); }
        return original;
    }
    private static Object emptyInventory() {
        try { return Class.forName("codechicken.nei.ItemStackAmount").newInstance(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static boolean canCraft(Object ref, Object gui) {
        if (!isCraftingTerm(gui)) {
            try { return (Boolean) Reflect.call(ref, "canCraft", gui); }
            catch (Exception e) { report("original canCraft", e); return false; }
        }
        try {
            if (Boolean.TRUE.equals(HALTED.get())) return false;
            Object overlay = Reflect.call(ref, "getOverlayHandler", gui);
            boolean supported = Reflect.is(overlay, "appeng.integration.modules.NEIHelpers.NEICraftingHandler")
                || (overlay != null && overlay.getClass().getName().equals("com.github.vfyjxf.nee.nei.NEECraftingTerminalHandler"));
            if (!supported) {
                notifyUnsupported(ref, gui);
                return false;
            }
            return Reflect.call(Reflect.get(ref, "handler"), "getResultStack", Reflect.get(ref, "recipeIndex")) != null;
        } catch (Exception e) { report("canCraft", e); return false; }
    }
    public static boolean craft(final Object ref, final Object gui, final int amount) {
        COMPLETED.remove();
        if (!isCraftingTerm(gui)) {
            try { return (Boolean) Reflect.call(ref, "craft", gui, amount); }
            catch (Exception e) { report("original craft", e); return false; }
        }
        if (!worker() || amount <= 0 || !canCraft(ref, gui)) return false;
        try {
            Object observedPlan = PLAN.get();
            if (observedPlan == null) {
                observedPlan = Reflect.get(Class.forName("codechicken.nei.recipe.AutoCraftingManager"), "math");
                PLAN.set(observedPlan);
            }
            final Object plan = observedPlan;
            BridgeNetwork.Result result = BridgeNetwork.request(gui, new Callable<BridgeNetwork.Request>() {
                public BridgeNetwork.Request call() throws Exception {
                    Object handler = Reflect.get(ref, "handler");
                    int index = ((Number) Reflect.get(ref, "recipeIndex")).intValue();
                    List<?> ingredients = (List<?>) Reflect.call(handler, "getIngredientStacks", index);
                    validateGrid(ingredients);
                    Object pos = Reflect.call(handler, "getResultStack", index);
                    Object stack = Reflect.get(pos, "item");
                    if (stack == null) return null;
                    Object nbt = Reflect.call(Class.forName("appeng.integration.modules.NEIHelpers.NEICraftingHandler"),
                        "packIngredients", gui, ingredients, true);
                    return new BridgeNetwork.Request(craftOperation(stack, plan), window(gui), Math.min(64, amount),
                        InventorySnapshot.compress(nbt), InventorySnapshot.writeStack(stack));
                }
            });
            if (result == null || result.status != 0) { HALTED.set(true); return false; }
            rememberSnapshot(result.snapshot);
            return recordCompleted(result.completed, Math.min(64, amount));
        } catch (Exception e) { HALTED.set(true); report("craft", e); return false; }
    }
    static boolean recordCompleted(int completed, int requested) {
        if (completed < 0 || completed > requested || requested < 1 || requested > 64)
            throw new IllegalArgumentException("Invalid confirmed craft count");
        COMPLETED.set(completed);
        return completed > 0;
    }
    /** Consume one successful native batch acknowledgement; ordinary NEI overlays retain their 64-step contract. */
    public static long completedCrafts() {
        Integer count = COMPLETED.get();
        COMPLETED.remove();
        return count == null ? 64L : count.longValue();
    }
    /** Recipe outputs live in master-root ingredients; initialItems becomes the iterator's inventory. */
    public static List<Object> goalItems(Object plan) {
        java.util.ArrayList<Object> goals = new java.util.ArrayList<Object>();
        if (plan == null) return goals;
        try {
            if (!Boolean.TRUE.equals(Reflect.call(plan, "hasMasterRoot"))) return java.util.Collections.emptyList();
            Object root = Reflect.get(plan.getClass(), "ROOT_RECIPE_ID");
            for (Object goal : (List<?>) Reflect.get(plan, "recipeIngredients")) {
                if (root != null && root.equals(Reflect.get(goal, "recipeId")))
                    goals.add(goal);
            }
            return goals;
        } catch (Exception e) {
            report("goal classification", e);
            return java.util.Collections.emptyList(); // Unknown plans must not relocate outputs.
        }
    }
    /** Shift+C plans keyed by identity; values are the root goals created before NEI raised satisfied ones. */
    private static final java.util.Map<Object, java.util.Set<Object>> UNRAISED_GOALS =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, java.util.Set<Object>>());
    public static Object ignoreInventoryRoot(Object plan, Object rootId) {
        try {
            java.util.Set<Object> goals = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            goals.addAll(goalItems(plan));
            UNRAISED_GOALS.put(plan, goals);
        } catch (Exception e) { report("craft items goals", e); }
        return rootId;
    }
    /**
     * Goals to retrieve from ME before planning. Shift+C replaces goals already met by the terminal's
     * ME + player view with a larger "next batch" goal; retrieving those would move the whole ME stock.
     */
    public static List<Object> retrievalGoals(Object plan) {
        List<Object> goals = goalItems(plan);
        java.util.Set<Object> unraised = plan == null ? null : UNRAISED_GOALS.get(plan);
        if (unraised == null) return goals;
        java.util.ArrayList<Object> kept = new java.util.ArrayList<Object>();
        for (Object goal : goals) if (unraised.contains(goal)) kept.add(goal);
        return kept;
    }
    public static List<Object> goalStacks(Object plan) {
        java.util.ArrayList<Object> stacks = new java.util.ArrayList<Object>();
        try { for (Object item : goalItems(plan)) stacks.add(Reflect.get(item, "itemStack")); }
        catch (Exception e) { report("goal classification", e); return java.util.Collections.emptyList(); }
        return stacks;
    }
    public static int craftOperation(Object stack, Object plan) throws Exception {
        List<Object> goals = goalStacks(plan);
        if (goals.isEmpty()) return 1; // An unknown/empty goal list must never relocate an output.
        for (Object goal : goals) {
            if (ServerCrafting.same(stack, goal)) return 1;
        }
        return 2;
    }
    static String[] unsupportedRecipe(Object ref) throws Exception {
        Object handler = Reflect.get(ref, "handler");
        Object result = Reflect.call(handler, "getResultStack", Reflect.get(ref, "recipeIndex"));
        Object item = result == null ? null : Reflect.get(result, "item");
        if (item == null) return null;
        String name = String.valueOf(Reflect.call(item, "func_82833_r|getDisplayName"));
        String recipe = String.valueOf(Reflect.call(handler, "getRecipeName"));
        return new String[]{name, recipe};
    }
    public static Object recipeReference(Object id, Object gui) throws Exception {
        boolean bridge = worker() && isCraftingTerm(gui);
        if (bridge && Boolean.TRUE.equals(HALTED.get())) return null;
        Object ref;
        try {
            ref = Reflect.call(Class.forName("codechicken.nei.recipe.RecipeHandlerRef"), "of", id);
        } catch (Exception e) {
            if (!bridge) throw e;
            // RestartableTask does not clear its thread field when execute() throws.
            // End this bridge run normally so the next shortcut can start a fresh task.
            HALTED.set(true);
            report("recipe lookup", e);
            return null;
        }
        if (ref == null && bridge && !Boolean.TRUE.equals(NOTIFIED.get())) {
            try {
                Object stack = Reflect.call(id, "getResult");
                String handler = String.valueOf(Reflect.call(id, "getHandlerName"));
                if (stack != null) {
                    String name = String.valueOf(Reflect.call(stack, "func_82833_r|getDisplayName"));
                    if (handler.startsWith("gt.recipe.")) {
                        String type = String.valueOf(Reflect.call(Class.forName("net.minecraft.util.StatCollector"), "func_74838_a|translateToLocal", handler));
                        notifyMessage("ae2nei.autocraft.unsupported", new String[]{name, type}, gui);
                    } else notifyMessage("ae2nei.autocraft.unresolved", new String[]{name}, gui);
                }
            } catch (Exception e) { report("unsupported recipe notice", e); }
        }
        return ref;
    }
    private static void notifyUnsupported(Object ref, final Object gui) {
        if (!worker() || Boolean.TRUE.equals(NOTIFIED.get())) return;
        try {
            final String[] names = unsupportedRecipe(ref);
            if (names == null) return;
            notifyMessage("ae2nei.autocraft.unsupported", names, gui);
        } catch (Exception e) { report("unsupported recipe notice", e); }
    }
    private static void notifyMessage(final String key, final String[] names, final Object gui) {
        if (Boolean.TRUE.equals(NOTIFIED.get())) return;
        NOTIFIED.set(true);
        BridgeNetwork.postClient(new Runnable() {
                public void run() {
                    try {
                        Object current = Reflect.call(Class.forName("codechicken.nei.NEIClientUtils"), "getGuiContainer");
                        if (current != gui) return;
                        Object mc = Reflect.get(gui, "field_146297_k", "mc");
                        Object player = Reflect.get(mc, "field_71439_g", "thePlayer");
                        if (player == null) return;
                        Object message = Class.forName("net.minecraft.util.ChatComponentTranslation")
                            .getConstructor(String.class, Object[].class)
                            .newInstance(key, (Object[]) names);
                        Reflect.call(player, "func_145747_a|addChatMessage", message);
                    } catch (Exception e) { report("unsupported recipe notice", e); }
                }
        });
    }
    static int window(Object gui) throws Exception {
        return ((Number) Reflect.get(Reflect.get(gui, "field_147002_h", "inventorySlots"), "field_75152_c", "windowId")).intValue();
    }
    static void validateGrid(List<?> ingredients) throws Exception {
        if (ingredients.isEmpty() || ingredients.size() > 9) throw new IllegalArgumentException("Not a 3x3 recipe");
        boolean[] occupied = new boolean[9];
        for (Object pos : ingredients) {
            int x = ((Number) Reflect.get(pos, "relx")).intValue() - 25;
            int y = ((Number) Reflect.get(pos, "rely")).intValue() - 6;
            if (x < 0 || y < 0 || x > 36 || y > 36 || x % 18 != 0 || y % 18 != 0)
                throw new IllegalArgumentException("Recipe does not use NEI's 3x3 crafting layout");
            int slot = x / 18 + y / 18 * 3;
            if (occupied[slot]) throw new IllegalArgumentException("Duplicate ingredient position");
            occupied[slot] = true;
        }
    }
    public static void report(String action, Exception e) {
        BridgeLog.warning(action, action + " failed: " + e, e);
    }
}
