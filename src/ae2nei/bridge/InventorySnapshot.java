package ae2nei.bridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** A server inventory observation, never a prediction or a local mutation. */
public final class InventorySnapshot {
    private InventorySnapshot() {}
    public static Object tag() throws Exception { return Class.forName("net.minecraft.nbt.NBTTagCompound").newInstance(); }
    public static byte[] compress(Object tag) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Reflect.call(Class.forName("net.minecraft.nbt.CompressedStreamTools"), "func_74799_a|writeCompressed", tag, out);
        byte[] bytes = out.toByteArray();
        if (bytes.length > BridgeNetwork.MAX_BYTES) throw new IllegalArgumentException("Inventory exceeds protocol limit");
        return bytes;
    }
    public static Object expand(byte[] bytes) throws Exception {
        if (bytes.length == 0 || bytes.length > BridgeNetwork.MAX_BYTES) throw new IllegalArgumentException("Invalid NBT size");
        return Reflect.call(Class.forName("net.minecraft.nbt.CompressedStreamTools"), "func_74796_a|readCompressed", new ByteArrayInputStream(bytes));
    }
    public static byte[] writeStack(Object stack) throws Exception {
        return compress(Reflect.call(stack, "func_77955_b|writeToNBT", tag()));
    }
    public static Object readStack(Object nbt) throws Exception {
        return Reflect.call(Class.forName("net.minecraft.item.ItemStack"), "func_77949_a|loadItemStackFromNBT", nbt);
    }
    static int size(Object stack) throws Exception {
        return stack == null ? 0 : ((Number) Reflect.get(stack, "field_77994_a", "stackSize")).intValue();
    }
    static Object storage(Object container) throws Exception {
        Object grid = Reflect.call(Reflect.call(container, "getNetworkNode"), "getGrid");
        Object cache = Reflect.call(grid, "getCache", Class.forName("appeng.api.networking.storage.IStorageGrid"));
        return Reflect.call(cache, "getItemInventory");
    }
    static Object filter(Object container) throws Exception {
        return Reflect.call(Class.forName("appeng.items.storage.ItemViewCell"), "createFilter", Reflect.call(container, "getViewCells"));
    }
    public static byte[] capture(Object container, Object player) throws Exception {
        Object entries = Class.forName("net.minecraft.nbt.NBTTagList").newInstance();
        Object filter = filter(container);
        int count = 0;
        for (Object ae : (Iterable<?>) Reflect.call(storage(container), "getStorageList")) {
            long quantity = ((Number) Reflect.call(ae, "getStackSize")).longValue();
            if (quantity <= 0 || (filter != null && !Boolean.TRUE.equals(Reflect.call(filter, "isListed", ae)))) continue;
            append(entries, Reflect.call(ae, "getItemStack"), quantity);
            if (++count > 16384) throw new IllegalArgumentException("Too many inventory entries");
        }
        Object playerInv = Reflect.get(player, "field_71071_by", "inventory");
        for (Object slot : (List<?>) Reflect.get(container, "field_75151_b", "inventorySlots")) {
            if (Reflect.get(slot, "field_75224_c", "inventory") == playerInv
                || Reflect.is(slot, "appeng.container.slot.SlotCraftingMatrix")) {
                Object stack = Reflect.call(slot, "func_75211_c|getStack");
                if (stack != null && size(stack) > 0) append(entries, stack, size(stack));
            }
        }
        Object root = tag();
        Reflect.call(root, "func_74782_a|setTag", "items", entries);
        return compress(root);
    }
    static void append(Object list, Object stack, long quantity) throws Exception {
        Object copy = Reflect.call(stack, "func_77946_l|copy");
        Reflect.set(copy, 1, "field_77994_a", "stackSize");
        Object entry = Reflect.call(copy, "func_77955_b|writeToNBT", tag());
        Reflect.call(entry, "func_74772_a|setLong", "bridgeQuantity", quantity);
        Reflect.call(list, "func_74742_a|appendTag", entry);
    }
    public static Object decode(byte[] bytes) throws Exception {
        Object inventory = Class.forName("codechicken.nei.ItemStackAmount").newInstance();
        Object entries = Reflect.call(expand(bytes), "func_150295_c|getTagList", "items", 10);
        int count = ((Number) Reflect.call(entries, "func_74745_c|tagCount")).intValue();
        for (int i = 0; i < count; i++) {
            Object entry = Reflect.call(entries, "func_150305_b|getCompoundTagAt", i);
            Object stack = readStack(entry);
            long quantity = ((Number) Reflect.call(entry, "func_74763_f|getLong", "bridgeQuantity")).longValue();
            if (stack != null && quantity > 0) Reflect.call(inventory, "add", stack, quantity);
        }
        return inventory;
    }
}
