
import java.lang.reflect.*;

public class TestProxyDefault {
    public static void main(String[] args) throws Exception {
        Class<?> handlerClass = Class.forName("codechicken.nei.api.IOverlayHandler");
        Object proxy = Proxy.newProxyInstance(
            handlerClass.getClassLoader(),
            new Class<?>[]{ handlerClass },
            new InvocationHandler() {
                public Object invoke(Object p, Method m, Object[] a) { return null; }
            }
        );
        for (Method m : proxy.getClass().getMethods()) {
            if ("transferRecipe".equals(m.getName())) {
                System.out.println("Declaring class: " + m.getDeclaringClass());
                System.out.println("isDefault: " + m.isDefault());
            }
        }
    }
}
