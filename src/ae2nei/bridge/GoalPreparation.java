package ae2nei.bridge;

import java.util.ArrayList;
import java.util.List;

/** Move available requested outputs into the player before NEI calculates missing crafts. */
public final class GoalPreparation {
    private static final int MAX_GOALS = 256;
    private final List<Object> stacks = new ArrayList<Object>();
    private final List<Long> quantities = new ArrayList<Long>();
    private int cursor;
    public static byte[] encode(Object plan) throws Exception {
        Object entries = Class.forName("net.minecraft.nbt.NBTTagList").newInstance();
        int count = 0;
        for (Object goal : AE2NEICraftingBridge.goalItems(plan)) {
            long quantity = ((Number) Reflect.call(goal, "getStackSize")).longValue();
            if (quantity <= 0) continue;
            if (++count > MAX_GOALS) throw new IllegalArgumentException("Too many output goals");
            Object stack = Reflect.call(Reflect.get(goal, "itemStack"), "func_77946_l|copy");
            Reflect.set(stack, 1, "field_77994_a", "stackSize");
            Object tag = Reflect.call(stack, "func_77955_b|writeToNBT", InventorySnapshot.tag());
            Reflect.call(tag, "func_74772_a|setLong", "bridgeGoalQuantity", quantity);
            Reflect.call(entries, "func_74742_a|appendTag", tag);
        }
        Object root = InventorySnapshot.tag();
        Reflect.call(root, "func_74782_a|setTag", "goals", entries);
        return InventorySnapshot.compress(root);
    }
    public GoalPreparation(byte[] bytes) throws Exception {
        Object root = InventorySnapshot.expand(bytes, BoundedNbt.RECIPE_BYTES);
        Object entries = Reflect.call(root, "func_150295_c|getTagList", "goals", 10);
        int count = ((Number) Reflect.call(entries, "func_74745_c|tagCount")).intValue();
        if (count > MAX_GOALS) throw new IllegalArgumentException("Too many output goals");
        for (int i = 0; i < count; i++) {
            Object entry = Reflect.call(entries, "func_150305_b|getCompoundTagAt", i);
            Object stack = InventorySnapshot.readStack(entry);
            long quantity = ((Number) Reflect.call(entry, "func_74763_f|getLong", "bridgeGoalQuantity")).longValue();
            if (stack == null || InventorySnapshot.size(stack) != 1 || quantity <= 0)
                throw new IllegalArgumentException("Invalid output goal");
            int existing = -1;
            for (int j = 0; j < stacks.size(); j++) if (ServerCrafting.same(stack, stacks.get(j))) { existing = j; break; }
            if (existing >= 0) quantities.set(existing, Math.addExact(quantities.get(existing), quantity));
            else { stacks.add(stack); quantities.add(quantity); }
        }
    }
    public boolean advance(Object container, Object player, Object energy, int slice) throws Exception {
        for (int step = 0; cursor < stacks.size() && step < slice; step++) {
            Object expected = stacks.get(cursor);
            long missing = quantities.get(cursor) - ServerCrafting.playerCount(container, player, expected);
            if (missing <= 0 || take(container, player, energy, expected, missing) == 0) cursor++;
        }
        return cursor >= stacks.size();
    }
    private static int take(Object container, Object player, Object energy, Object expected, long missing) throws Exception {
        Object inventory = Reflect.get(player, "field_71071_by", "inventory");
        int max = Math.min(64, Math.min(((Number) Reflect.call(inventory, "func_70297_j_|getInventoryStackLimit")).intValue(),
            ((Number) Reflect.call(expected, "func_77976_d|getMaxStackSize")).intValue()));
        if (max <= 0) return 0;
        int wanted = (int) Math.min(missing, max);
        int[] rooms = new int[36];
        int space = 0;
        for (int i = 0; i < 36; i++) {
            Object stack = Reflect.call(inventory, "func_70301_a|getStackInSlot", i);
            rooms[i] = stack == null ? max : ServerCrafting.same(expected, stack) ? Math.max(0, max - InventorySnapshot.size(stack)) : 0;
            space += rooms[i];
        }
        wanted = Math.min(wanted, space);
        if (wanted <= 0) return 0;
        Object ae = Reflect.call(Class.forName("appeng.util.item.AEItemStack"), "create", expected);
        Object filter = InventorySnapshot.filter(container);
        if (filter != null && !Boolean.TRUE.equals(Reflect.call(filter, "isListed", ae))) return 0;
        Reflect.call(ae, "setStackSize", (long) wanted);
        Object extracted = Reflect.call(Class.forName("appeng.util.Platform"), "poweredExtraction", energy,
            InventorySnapshot.storage(container), ae, Reflect.call(container, "getActionSource"));
        if (extracted == null) return 0;
        Object actual = Reflect.call(extracted, "getItemStack");
        int taken = InventorySnapshot.size(actual);
        if (!ServerCrafting.same(expected, actual) || taken <= 0 || taken > wanted)
            throw new IllegalStateException("Invalid ME extraction result");
        int left = taken;
        // Fill existing stacks first, then empty slots. The main player inventory is changed only on this tick thread.
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < 36 && left > 0; i++) {
            Object stack = Reflect.call(inventory, "func_70301_a|getStackInSlot", i);
            if (rooms[i] <= 0 || (pass == 0 ? stack == null : stack != null)) continue;
            int placed = Math.min(left, rooms[i]);
            if (stack == null) { stack = Reflect.call(actual, "func_77946_l|copy"); Reflect.set(stack, placed, "field_77994_a", "stackSize"); }
            else Reflect.set(stack, InventorySnapshot.size(stack) + placed, "field_77994_a", "stackSize");
            Reflect.call(inventory, "func_70299_a|setInventorySlotContents", i, stack);
            left -= placed;
        }
        Reflect.call(inventory, "func_70296_d|markDirty");
        if (left != 0) throw new IllegalStateException("Player inventory changed during ME extraction");
        return taken;
    }
}
