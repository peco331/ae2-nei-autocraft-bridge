package codechicken.nei.bookmark;
import java.util.*;
import net.minecraft.item.ItemStack;
import codechicken.nei.recipe.*;
/** Value fixture for simple solid materials. RecipeChainMath and Iterator are read unchanged from the NEI JAR. */
public class BookmarkItem {
    public enum BookmarkItemType { ITEM, RESULT, INGREDIENT }
    public final int groupId,fluidCellAmount=1;
    public final ItemStack itemStack;
    public final Recipe.RecipeId recipeId;
    public final BookmarkItemType type;
    public final long factor,chance=10000;
    public long multiplier;
    public final Map<String,ItemStack> permutations;
    BookmarkItem(Builder b){groupId=b.groupId;itemStack=b.stack;recipeId=b.recipeId;type=b.type;factor=b.factor;multiplier=b.multiplier<0?b.stack.field_77994_a/b.factor:b.multiplier;permutations=b.perms==null?Builder.generatePermutations(itemStack):b.perms;}
    public static Builder builder(int groupId,ItemStack stack){return new Builder(groupId,stack);}
    public static BookmarkItem of(int groupId,ItemStack stack){return builder(groupId,stack).build();}
    public BookmarkItem copy(){return copyWithMultiplier(multiplier);}
    public BookmarkItem copyWithMultiplier(long amount){return builder(groupId,itemStack).recipeId(recipeId).type(type).factor(factor).multiplier(amount).permutations(permutations).build();}
    public boolean containsItems(BookmarkItem other){for(String key:permutations.keySet())if(other.permutations.containsKey(key))return true;return false;}
    public boolean emptyFactor(){return factor<=0;}
    public long getAmount(){return getAmount(multiplier);}
    public long getAmount(long count){return factor*count;}
    public long getAmount(long count,ItemStack permutation){return getAmount(count);}
    public long getStackSize(){return getAmount();}
    public long getStackSize(long amount){return amount;}
    public long getMultiplier(){return multiplier;}
    public long getMultiplierFromAmount(long amount){return (amount+factor-1)/factor;}
    public ItemStack getItemStack(long amount){return StackInfo.withAmount(itemStack,amount);}
    public boolean equals(Object o){if(!(o instanceof BookmarkItem))return false;BookmarkItem b=(BookmarkItem)o;return groupId==b.groupId&&type==b.type&&itemStack.item==b.itemStack.item&&Objects.equals(recipeId,b.recipeId);}
    public int hashCode(){return Objects.hash(groupId,type,itemStack.item,recipeId);}
    public static class Builder {
        final int groupId;final ItemStack stack;long factor=1,multiplier=-1;Recipe.RecipeId recipeId;BookmarkItemType type=BookmarkItemType.ITEM;Map<String,ItemStack> perms;
        Builder(int groupId,ItemStack stack){this.groupId=groupId;this.stack=stack;}
        public Builder recipeId(Recipe.RecipeId id){recipeId=id;return this;}
        public Builder factor(long value){factor=value;return this;}
        public Builder multiplier(long value){multiplier=value;return this;}
        public Builder type(BookmarkItemType value){type=value;return this;}
        public Builder permutations(Map<String,ItemStack> value){perms=value;return this;}
        public BookmarkItem build(){return new BookmarkItem(this);}
        public static Map<String,ItemStack> generatePermutations(ItemStack stack){return Collections.singletonMap(stack.item.name,stack);}
    }
}
