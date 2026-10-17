package codechicken.nei;
import java.util.Objects;
import net.minecraft.item.ItemStack;
public class NEIClientUtils {
    public static boolean areStacksSameTypeCraftingWithNBT(ItemStack a,ItemStack b){return a!=null&&b!=null&&a.item==b.item&&Objects.equals(a.tag,b.tag);}
    public static boolean areStacksSameTypeWithNBT(ItemStack a,ItemStack b){return areStacksSameTypeCraftingWithNBT(a,b);}
}
