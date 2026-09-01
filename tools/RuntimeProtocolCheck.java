import com.yagay.intentcleaner.domain.RuntimeProtocol;

public final class RuntimeProtocolCheck {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    public static void main(String[] args) {
        check(RuntimeProtocol.supportsSafetyPause("STALE", 19));
        check(RuntimeProtocol.supportsSafetyPause("UP_TO_DATE", 20));
        check(!RuntimeProtocol.supportsSafetyPause("STALE", 18));
        check(RuntimeProtocol.supportsSafetyPause("STALE", 21));
        check(!RuntimeProtocol.supportsSafetyPause("STALE", 22));
        check(!RuntimeProtocol.supportsSafetyPause("RELOADING", 19));
        check(!RuntimeProtocol.supportsSafetyPause("FAILED", 19));
        check(RuntimeProtocol.current("UP_TO_DATE", 19, 19));
        check(!RuntimeProtocol.current("STALE", 19, 19));
        check(!RuntimeProtocol.current("UP_TO_DATE", 17, 19));
        check(!RuntimeProtocol.current("UP_TO_DATE", 20, 19));
        check(!RuntimeProtocol.current("RELOADING", 19, 19));
        check(!RuntimeProtocol.current("FAILED", 19, 19));
        check(!RuntimeProtocol.current(null, 19, 19));
        check(RuntimeProtocol.digest("").equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        check(RuntimeProtocol.digest("abc").equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"));
        check(RuntimeProtocol.digest("规则").length() == 64);
        check(!RuntimeProtocol.digest("{}").equals(RuntimeProtocol.digest("{ }")));
        check(RuntimeProtocol.digest("{\"rules\":[]}").equals(RuntimeProtocol.digest("{\"rules\":[]}")));
        System.out.println("RuntimeProtocolCheck: " + checks + " passed");
    }
}
