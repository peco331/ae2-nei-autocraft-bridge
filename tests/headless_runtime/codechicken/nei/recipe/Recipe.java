package codechicken.nei.recipe;
import java.util.*;
import net.minecraft.item.ItemStack;
public class Recipe {
    public static class RecipeId {
        public final String name;public final ItemStack result;
        public RecipeId(String name,ItemStack result){this.name=name;this.result=result;}
        public static RecipeId of(Object result,String name,Iterable<?> ingredients){return new RecipeId(name,(ItemStack)result);}
        public boolean equals(Object other){return other instanceof RecipeId&&name.equals(((RecipeId)other).name);}
        public int hashCode(){return name.hashCode();}
    }
    public static class RecipeIngredient {
        public ItemStack getItemStack(){throw new UnsupportedOperationException();}
    }
}
