package ae2nei.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime names only: this coremod is deliberately excluded from FML remapping. */
public final class Reflect {
    private Reflect() {}
    private static final ClassValue<ConcurrentHashMap<String, Field>> FIELDS = new ClassValue<ConcurrentHashMap<String, Field>>() {
        protected ConcurrentHashMap<String, Field> computeValue(Class<?> type) { return new ConcurrentHashMap<String, Field>(); }
    };
    private static final ClassValue<ConcurrentHashMap<String, Method>> METHODS = new ClassValue<ConcurrentHashMap<String, Method>>() {
        protected ConcurrentHashMap<String, Method> computeValue(Class<?> type) { return new ConcurrentHashMap<String, Method>(); }
    };
    private static final ClassValue<Method[]> PUBLIC_METHODS = new ClassValue<Method[]>() {
        protected Method[] computeValue(Class<?> type) { return type.getMethods(); }
    };
    private static Field field(Class<?> type, String... names) throws Exception {
        String key = java.util.Arrays.toString(names);
        Field cached = FIELDS.get(type).get(key);
        if (cached != null) return cached;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name); f.setAccessible(true); FIELDS.get(type).putIfAbsent(key, f); return f;
                } catch (NoSuchFieldException ignored) {}
            }
        }
        throw new NoSuchFieldException(type.getName() + ": " + key);
    }
    public static void set(Object target, Object value, String... names) throws Exception {
        field(target.getClass(), names).set(target, value);
    }
    public static Object get(Object target, String... names) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        return field(type, names).get(target instanceof Class ? null : target);
    }
    public static Object call(Object target, String names, Object... args) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        StringBuilder keyBuilder = new StringBuilder(names);
        for (Object arg : args) keyBuilder.append(':').append(arg == null ? "null" : arg.getClass().getName());
        String key = keyBuilder.toString();
        Method cached = METHODS.get(type).get(key);
        if (cached != null) return cached.invoke(target instanceof Class ? null : target, args);
        for (String name : names.split("\\|")) {
            for (Method m : PUBLIC_METHODS.get(type)) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
                Class<?>[] params = m.getParameterTypes();
                boolean match = true;
                for (int i = 0; i < params.length; i++) {
                    Class<?> p = params[i];
                    if (p == int.class) p = Integer.class;
                    if (p == long.class) p = Long.class;
                    if (p == boolean.class) p = Boolean.class;
                    if (p == short.class) p = Short.class;
                    if (p == double.class) p = Double.class;
                    if (p == float.class) p = Float.class;
                    if (p == byte.class) p = Byte.class;
                    if (p == char.class) p = Character.class;
                    if (args[i] == null && params[i].isPrimitive()) match = false;
                    if (args[i] != null && !p.isInstance(args[i])) match = false;
                }
                if (match) { m.setAccessible(true); METHODS.get(type).putIfAbsent(key, m); return m.invoke(target instanceof Class ? null : target, args); }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + names);
    }
    public static boolean is(Object value, String type) throws Exception {
        return value != null && Class.forName(type).isInstance(value);
    }
}
