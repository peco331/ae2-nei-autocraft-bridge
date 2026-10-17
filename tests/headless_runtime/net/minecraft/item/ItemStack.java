package net.minecraft.item;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
public class ItemStack {
    public int field_77994_a;
    public final Item item;
    public NBTTagCompound tag;
    public ItemStack(Item item,int amount){this.item=item;field_77994_a=amount;}
    public ItemStack(Block block){this(new Item(block.name),1);}
    public Item func_77973_b(){return item;}
    public ItemStack func_77946_l(){ItemStack copy=new ItemStack(item,field_77994_a);copy.tag=tag;return copy;}
    public NBTTagCompound func_77978_p(){return tag;}
}
