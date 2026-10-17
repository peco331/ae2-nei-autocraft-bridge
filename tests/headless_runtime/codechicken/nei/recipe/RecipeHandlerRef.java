package codechicken.nei.recipe;
public class RecipeHandlerRef {
    public static volatile boolean fail;
    public static RecipeHandlerRef of(Recipe.RecipeId id){if(fail)throw new IllegalStateException("scripted lookup failure");return new RecipeHandlerRef();}
}
