package net.minecraft.nbt;
import java.util.*;
/** Only the value operations needed by the upstream planning classes; no game bootstrap. */
public class NBTTagCompound {
    public final Map<String,Object> values=new HashMap<String,Object>();
    public boolean func_74764_b(String key){return values.containsKey(key);}
    public NBTTagCompound func_74775_l(String key){return (NBTTagCompound)values.get(key);}
    public long func_74763_f(String key){Object v=values.get(key);return v==null?0:((Number)v).longValue();}
    public void func_74772_a(String key,long value){values.put(key,value);}
    public boolean equals(Object other){return other instanceof NBTTagCompound&&values.equals(((NBTTagCompound)other).values);}
    public int hashCode(){return values.hashCode();}
}
