import com.yagay.ListCleaner.xposed.OrderingAccess;

public final class OrderingAccessCheck {
    private static final Object INTENT = new Object();
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }
    private static class Modern {
        protected Object getTargetIntent() { return INTENT; }
        private Object mContext = INTENT;
    }
    private static final class Child extends Modern {}
    private static class FieldOnly { private Object mTargetIntent = INTENT; }
    private static final class FieldChild extends FieldOnly {}
    private static final class Communicator {
        public Object getTargetIntent() { return INTENT; }
    }
    private static class Legacy { private Object mResolverListCommunicator = new Communicator(); }
    private static final class LegacyChild extends Legacy {}
    private static final class Throwing {
        protected Object getTargetIntent() { throw new IllegalStateException("fixture"); }
        private Object mTargetIntent = INTENT;
    }
    public static void main(String[] args) throws Exception {
        check(OrderingAccess.targetIntent(new Modern()) == INTENT);
        check(OrderingAccess.targetIntent(new Child()) == INTENT);
        check(OrderingAccess.targetIntent(new FieldChild()) == INTENT);
        check(OrderingAccess.targetIntent(new LegacyChild()) == INTENT);
        check(OrderingAccess.field(new Child(), "mContext") == INTENT);
        check(!OrderingAccess.isChooser(new Object()));
        try { OrderingAccess.targetIntent(new Object()); throw new AssertionError(); }
        catch (NoSuchFieldException expected) { checks++; }
        try { OrderingAccess.targetIntent(new Throwing()); throw new AssertionError(); }
        catch (java.lang.reflect.InvocationTargetException expected) { checks++; }
        System.out.println("OrderingAccessCheck: " + checks + " passed");
    }
}
