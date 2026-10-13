package ae2nei.bridge;

import java.util.List;
import java.util.concurrent.Callable;

/** NEI's worker waits for authoritative server results; the render thread never waits. */
public final class AE2NEICraftingBridge {
    private AE2NEICraftingBridge() {}
    private static final ThreadLocal<Boolean> HALTED = new ThreadLocal<Boolean>();
    private static final ThreadLocal<byte[]> SNAPSHOT = new ThreadLocal<byte[]>();
    private static final ThreadLocal<Object> PLAN = new ThreadLocal<Object>();
    public static void beginProcessing() { HALTED.remove(); SNAPSHOT.remove(); PLAN.remove(); }
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
            return (Reflect.is(overlay, "appeng.integration.modules.NEIHelpers.NEICraftingHandler")
                || (overlay != null && overlay.getClass().getName().equals("com.github.vfyjxf.nee.nei.NEECraftingTerminalHandler")))
                && Reflect.call(Reflect.get(ref, "handler"), "getResultStack", Reflect.get(ref, "recipeIndex")) != null;
        } catch (Exception e) { report("canCraft", e); return false; }
    }
    public static boolean craft(final Object ref, final Object gui, final int amount) {
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
            return result.completed > 0;
        } catch (Exception e) { HALTED.set(true); report("craft", e); return false; }
    }
    /** NEI initialItems are the user's goals; every other output is an intermediate. */
    public static int craftOperation(Object stack, Object plan) throws Exception {
        if (plan == null) return 1;
        List<?> goals = (List<?>) Reflect.get(plan, "initialItems");
        if (goals.isEmpty()) return 1; // An unknown/empty goal list must never relocate an output.
        for (Object goal : goals) {
            if (ServerCrafting.same(stack, Reflect.get(goal, "itemStack"))) return 1;
        }
        return 2;
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
