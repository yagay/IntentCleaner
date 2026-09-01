package com.yagay.intentcleaner.xposed;

import com.yagay.intentcleaner.domain.TilePolicy;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Presentation-only adapters. No QSHost, service lifecycle or settings writes. */
public final class TileEditorAccess {
    private TileEditorAccess() {}

    public static final class LegacyChange {
        private final Field field;
        private final Object target;
        private final Object original;
        private final Object replacement;
        public final int removed;
        LegacyChange(Field field, Object target, List<?> original, List<?> replacement) {
            this.field = field; this.target = target; this.original = original;
            this.replacement = replacement; removed = original.size() - replacement.size();
        }
        public void apply() throws IllegalAccessException { field.set(target, replacement); }
        public void restore() throws IllegalAccessException { field.set(target, original); }
    }

    public static LegacyChange prepareLegacyHierarchy(Object adapter, Set<String> hidden) throws ReflectiveOperationException {
        for (Class<?> type = adapter.getClass(); type != null; type = type.getSuperclass()) {
            try { return prepareLegacy(adapter, hidden, type.getDeclaredField("mAllTiles")); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException("mAllTiles");
    }

    private static LegacyChange prepareLegacy(Object adapter, Set<String> hidden, Field allField) throws ReflectiveOperationException {
        allField.setAccessible(true);
        Object allValue = allField.get(adapter);
        Object currentValue = OrderingAccess.field(adapter, "mCurrentSpecs");
        if (!(allValue instanceof List<?>) || !(currentValue instanceof List<?>)) return null;
        Set<String> current = new HashSet<>();
        for (Object spec : (List<?>) currentValue) {
            if (!(spec instanceof String) || TilePolicy.canonical((String) spec) == null)
                throw new IllegalArgumentException("Unknown current tile identity");
            current.add(TilePolicy.canonical((String) spec));
        }
        List<?> all = (List<?>) allValue;
        List<Object> filtered = new ArrayList<>();
        for (Object tile : all) {
            Object raw = OrderingAccess.field(tile, "spec");
            if (!(raw instanceof String)) throw new IllegalArgumentException("Unknown tile identity");
            String spec = TilePolicy.canonical((String) raw);
            if (TilePolicy.keep((String) raw, current.contains(spec), hidden)) filtered.add(tile);
        }
        return filtered.size() == all.size() ? null : new LegacyChange(allField, adapter, all, filtered);
    }

    public static List<?> filterModern(List<?> tiles, Set<String> hidden) throws ReflectiveOperationException {
        List<Object> filtered = new ArrayList<>();
        for (Object tile : tiles) {
            Object current = OrderingAccess.call(tile, "isCurrent");
            Object spec = OrderingAccess.call(OrderingAccess.call(tile, "getTileSpec"), "getSpec");
            if (!(current instanceof Boolean) || !(spec instanceof String))
                throw new IllegalArgumentException("Unknown edit tile model");
            if (TilePolicy.keep((String) spec, (Boolean) current, hidden)) filtered.add(tile);
        }
        return filtered.size() == tiles.size() ? tiles : filtered;
    }

    /** Use the host's interfaces/continuation, never cast its Flow to our Kotlin runtime. */
    public static Object wrapFlow(Object upstream, Class<?> flowClass, Class<?> collectorClass,
            Function<List<?>, List<?>> filter) {
        return Proxy.newProxyInstance(flowClass.getClassLoader(), new Class<?>[]{flowClass}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
            if (!method.getName().equals("collect") || args == null || args.length != 2)
                return invoke(method, upstream, args);
            Object downstream = args[0];
            Object collector = Proxy.newProxyInstance(collectorClass.getClassLoader(), new Class<?>[]{collectorClass}, (self, emit, values) -> {
                if (emit.getDeclaringClass() == Object.class) return objectMethod(self, emit, values);
                Object[] passed = values;
                if (emit.getName().equals("emit") && values != null && values.length == 2 && values[0] instanceof List<?>) {
                    passed = values.clone();
                    passed[0] = filter.apply((List<?>) values[0]);
                }
                return invoke(emit, downstream, passed);
            });
            Object[] passed = args.clone(); passed[0] = collector;
            return invoke(method, upstream, passed);
        });
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            default: return "IntentcleanerTileEditorFlow";
        }
    }
    private static Object invoke(Method method, Object receiver, Object[] args) throws Throwable {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
