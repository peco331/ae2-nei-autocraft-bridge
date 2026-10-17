package net.minecraft.item;
import java.util.*;
public class Item {
    public static final Map<String,Item> REGISTRY=new HashMap<String,Item>();
    public final String name;
    public Item(String name){this.name=name;REGISTRY.put(name,this);}
    public boolean hasContainerItem(ItemStack stack){return false;}
    public ItemStack getContainerItem(ItemStack stack){return null;}
}
