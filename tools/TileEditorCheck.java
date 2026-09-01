import com.yagay.intentcleaner.domain.TilePolicy;
import com.yagay.intentcleaner.xposed.TileEditorAccess;
import java.util.*;

public final class TileEditorCheck {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private static final class Tile { private final String spec; Tile(String spec) { this.spec = spec; } }
    private static class Legacy {
        private List<?> mAllTiles;
        private List<?> mCurrentSpecs;
        Legacy(List<?> all, List<?> current) { mAllTiles = all; mCurrentSpecs = current; }
    }
    private static final class Child extends Legacy { Child(List<?> a, List<?> b) { super(a,b); } }
    private static final class Spec {
        private final String spec;
        Spec(String value) { spec = value; }
        public String getSpec() { return spec; }
    }
    private static final class Model {
        private final Spec tileSpec; private final boolean current;
        Model(String spec, boolean current) { tileSpec = new Spec(spec); this.current = current; }
        public Spec getTileSpec() { return tileSpec; }
        public boolean isCurrent() { return current; }
    }
    public interface Flow { Object collect(Collector collector, Object continuation); }
    public interface Collector { Object emit(Object value, Object continuation); }

    public static void main(String[] args) throws Exception {
        String canonical = "custom(com.example/com.example.Tile)";
        check(canonical.equals(TilePolicy.canonical("custom(com.example/.Tile)")));
        check(canonical.equals(TilePolicy.canonical(canonical)));
        check("wifi".equals(TilePolicy.canonical(" wifi ")));
        for (String bad : Arrays.asList(null, "", "custom(foo)", "custom(com.example/)", "bad\nvalue", "a/b", "*"))
            check(TilePolicy.canonical(bad) == null);
        Set<String> hidden = Set.of("wifi", canonical);
        check(TilePolicy.keep("wifi", true, hidden));
        check(!TilePolicy.keep("wifi", false, hidden));
        check(!TilePolicy.keep("custom(com.example/.Tile)", false, hidden));
        check(TilePolicy.keep("bt", false, hidden));
        check(TilePolicy.keep(null, false, hidden));

        Tile wifi = new Tile("wifi"), custom = new Tile("custom(com.example/.Tile)"), bt = new Tile("bt");
        List<Tile> originals = List.of(wifi, custom, bt);
        Child adapter = new Child(originals, List.of("wifi"));
        TileEditorAccess.LegacyChange change = TileEditorAccess.prepareLegacyHierarchy(adapter, hidden);
        check(change.removed == 1);
        check(((Legacy) adapter).mAllTiles == originals);
        change.apply();
        check(((Legacy) adapter).mAllTiles.equals(List.of(wifi, bt)));
        check(((Legacy) adapter).mCurrentSpecs.equals(List.of("wifi")));
        change.restore(); check(((Legacy) adapter).mAllTiles == originals);
        check(TileEditorAccess.prepareLegacyHierarchy(adapter, Set.of()) == null);
        check(TileEditorAccess.prepareLegacyHierarchy(new Legacy(originals, null), hidden) == null);
        try { TileEditorAccess.prepareLegacyHierarchy(new Legacy(originals, List.of(1)), hidden); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }

        Model fixed = new Model("wifi", true), available = new Model(canonical, false), other = new Model("bt", false);
        List<Model> models = List.of(fixed, available, other);
        check(TileEditorAccess.filterModern(models, hidden).equals(List.of(fixed, other)));
        check(TileEditorAccess.filterModern(models, Set.of()) == models);
        check(models.size() == 3);
        Object continuation = new Object(), suspended = new Object();
        int[] emitted = {0};
        Flow source = (collector, passed) -> { check(passed == continuation); return collector.emit(models, passed); };
        Flow wrapped = (Flow) TileEditorAccess.wrapFlow(source, Flow.class, Collector.class, list -> {
            try { return TileEditorAccess.filterModern(list, hidden); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
        Object result = wrapped.collect((value, passed) -> {
            emitted[0]++; check(passed == continuation); check(value.equals(List.of(fixed, other))); return suspended;
        }, continuation);
        check(result == suspended && emitted[0] == 1);
        RuntimeException cancellation = new RuntimeException("cancel");
        try { wrapped.collect((v, c) -> { throw cancellation; }, continuation); throw new AssertionError(); }
        catch (RuntimeException expected) { check(expected == cancellation); }
        check(wrapped.equals(wrapped)); check(!wrapped.equals(source));
        System.out.println("TileEditorCheck: " + checks + " passed");
    }
}
