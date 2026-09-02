import com.yagay.intentcleaner.domain.ComponentStatePolicy;
import com.yagay.intentcleaner.data.ComponentRootCommand;

public class ComponentStateCheck {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("case " + checks); }
    public static void main(String[] args) throws Exception {
        check(ComponentStatePolicy.enabled(0, true));
        check(!ComponentStatePolicy.enabled(0, false));
        check(ComponentStatePolicy.enabled(1, false));
        for (int state : new int[]{2,3,4}) for (boolean manifest : new boolean[]{true,false}) check(!ComponentStatePolicy.enabled(state,manifest));
        check(ComponentStatePolicy.enabled(-1,true) == null);
        check(ComponentStatePolicy.enabled(5,false) == null);
        String script = ComponentStatePolicy.command("com.example", "com.example.Outer$Tile", 10, false);
        check(script.contains("disable --user 10 'com.example/com.example.Outer$Tile'"));
        check(ComponentStatePolicy.command("com.example", "com.example.Tile", 0, true).contains("pm enable --user 0"));
        for (String bad : new String[]{"", ".Tile", "Tile;id", "x.y'", "a.b\n", "a.b/c", "a.b$(id)", "a.b`id`"})
            check(!ComponentStatePolicy.valid("com.example",bad,0));
        check(!ComponentStatePolicy.valid("bad/pkg", "a.b",0));
        check(!ComponentStatePolicy.valid("com.example", "a.b",-1));
        check(!ComponentStatePolicy.valid("com.example", "a.b",21475));
        try { ComponentStatePolicy.command("bad/pkg", "a.b",0,false); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { checks++; }
        // Test process management only. Never execute su or Android pm on this host.
        ComponentRootCommand.Result ok = ComponentRootCommand.capture(new ProcessBuilder("sh", "-c", "printf test"),2);
        check(ok.exitCode == 0 && !ok.timedOut && ok.output.equals("test"));
        ComponentRootCommand.Result failed = ComponentRootCommand.capture(new ProcessBuilder("sh", "-c", "exit 77"),2);
        check(failed.exitCode == 77);
        ComponentRootCommand.Result timeout = ComponentRootCommand.capture(new ProcessBuilder("sh", "-c", "exec sleep 3"),1);
        check(timeout.timedOut && timeout.exitCode == -1);
        System.out.println("PASS: " + checks + " component state, command validation and bounded process cases");
    }
}
