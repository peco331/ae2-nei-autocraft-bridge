package codechicken.nei.recipe;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
public class StackInfo {
    private static boolean paused;
    public static boolean isPausedItemDamageSound(){return paused;}
    public static void pauseItemDamageSound(boolean value){paused=value;}
    public static ItemStack withAmount(ItemStack stack,long amount){return new ItemStack(stack.item,(int)amount);}
    public static int getAmount(ItemStack stack){return stack.field_77994_a;}
    public static NBTTagCompound itemStackToNBT(ItemStack stack,boolean size){NBTTagCompound n=new NBTTagCompound();n.values.put("item",stack.item.name);if(size)n.values.put("amount",stack.field_77994_a);return n;}
    public static ItemStack loadFromNBT(NBTTagCompound tag,long amount){return new ItemStack(Item.REGISTRY.get(tag.values.get("item")),(int)amount);}
}
