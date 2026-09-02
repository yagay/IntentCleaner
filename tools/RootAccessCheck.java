package com.yagay.ListCleaner.data;

/** Exercises denied, non-root and timed-out responses without invoking su. */
public final class RootAccessCheck {
    public static void main(String[] args) {
        ComponentRootCommand.verifyRoot(new ComponentRootCommand.Result(0, false, ""));
        denied(new ComponentRootCommand.Result(1, false, "Permission denied"), "未获得 Root 权限");
        denied(new ComponentRootCommand.Result(77, false, "private diagnostic"), "未获得 Root 权限");
        denied(new ComponentRootCommand.Result(-1, true, ""), "等待 Root 授权超时");
        // A timeout must never be mistaken for success, even with exit code zero.
        denied(new ComponentRootCommand.Result(0, true, ""), "等待 Root 授权超时");
        System.out.println("PASS: Root grant, denial, non-root and timeout guidance");
    }

    private static void denied(ComponentRootCommand.Result result, String expected) {
        try {
            ComponentRootCommand.verifyRoot(result);
            throw new AssertionError("Root failure accepted");
        } catch (ComponentRootCommand.RootAccessException failure) {
            String text = failure.getMessage();
            if (!text.contains(expected) || !text.contains("KernelSU / Magisk") ||
                    !text.contains("尚未更改组件") || !text.contains("LSPosed") ||
                    text.contains("private diagnostic")) {
                throw new AssertionError("Missing or unsafe authorization guidance");
            }
        }
    }
}
