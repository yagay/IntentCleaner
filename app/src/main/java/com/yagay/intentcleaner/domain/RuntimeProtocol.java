package com.yagay.intentcleaner.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** A read-only, UID-authenticated system hook probe; never an activity launch. */
public final class RuntimeProtocol {
    public static final String ACTION = "com.yagay.intentcleaner.action.RUNTIME_PROBE_V1";
    public static final String PACKAGE = "com.yagay.intentcleaner";
    public static final String COMPONENT = PACKAGE + ".RuntimeProbe";
    private RuntimeProtocol() {}

    public static String digest(String config) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(config.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static boolean current(String state, long loaded, long installed) {
        return "UP_TO_DATE".equals(state) && loaded == installed;
    }

    /** Explicit compatibility allowlist; do not infer compatibility for future schemas. */
    public static boolean supportsSafetyPause(String state, long loaded) {
        return ("UP_TO_DATE".equals(state) || "STALE".equals(state)) && (loaded == 19 || loaded == 20 || loaded == 21 || loaded == 22 || loaded == 23 || loaded == 24);
    }
}
