package com.yagay.ListCleaner.data;

import com.yagay.ListCleaner.ui.DiagnosticBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** One explicit user action per invocation; bounded output/time, no persistent root daemon. */
public final class ComponentRootCommand {
    private ComponentRootCommand() {}
    public static final class RootAccessException extends IllegalStateException {
        RootAccessException(String message) { super(message); }
    }
    private static final String ROOT_GUIDANCE =
        "请打开 KernelSU / Magisk 等 Root 管理器，为“列表清理”开启超级用户权限，再返回重试。仅启用 LSPosed 模块不能代替 Root 授权。";

    /** Read-only check for each explicit batch; do not cache authorization across actions. */
    public static void requireRoot() {
        final Result result;
        try {
            result = run("test \"$(id -u)\" = 0");
        } catch (Exception failure) {
            throw new RootAccessException("无法取得 Root 权限。" + ROOT_GUIDANCE);
        }
        verifyRoot(result);
    }
    static void verifyRoot(Result result) {
        if (result.timedOut) {
            throw new RootAccessException("等待 Root 授权超时，尚未更改组件。" + ROOT_GUIDANCE);
        }
        if (result.exitCode != 0) {
            throw new RootAccessException("未获得 Root 权限，尚未更改组件。" + ROOT_GUIDANCE);
        }
    }
    public static final class Result {
        public final int exitCode;
        public final boolean timedOut;
        public final String output;
        Result(int code, boolean timeout, String text) { exitCode = code; timedOut = timeout; output = text; }
    }
    public static Result run(String script) throws Exception {
        return capture(new ProcessBuilder("su", "-c", script), 25);
    }
    public static Result capture(ProcessBuilder builder, int seconds) throws Exception {
        DiagnosticBuffer buffer = new DiagnosticBuffer(16 * 1024);
        AtomicReference<Exception> error = new AtomicReference<>();
        Process process = builder.redirectErrorStream(true).start();
        Thread reader = new Thread(() -> {
            try (java.io.InputStream in = process.getInputStream()) {
                byte[] bytes = new byte[2048]; int size;
                while ((size = in.read(bytes)) >= 0) buffer.append(bytes, size);
            } catch (Exception failure) { error.set(failure); }
        }, "component-root-output");
        reader.setDaemon(true);
        reader.start();
        try {
            boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            reader.join(1000);
            String text = new String(buffer.snapshot(), StandardCharsets.UTF_8);
            if (buffer.truncated()) text += "\n[output truncated]";
            if (reader.isAlive() || error.get() != null) text += "\n[output incomplete]";
            return new Result(finished ? process.exitValue() : -1, !finished, text);
        } finally {
            process.destroy();
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
        }
    }
}
