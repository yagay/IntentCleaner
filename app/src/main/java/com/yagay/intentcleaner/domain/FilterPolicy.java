package com.yagay.intentcleaner.domain;

/** Pure policy, independent of Android and covered by the host-side regression harness. */
public final class FilterPolicy {
    private FilterPolicy() {}

    public static boolean sameCaller(int callerUid, int targetUid) {
        return callerUid >= 0 && callerUid == targetUid;
    }

    public static boolean restoreEmpty(String kind, int before, int after) {
        // Text actions are optional menu entries, not a mandatory file-open destination.
        return before > 0 && after == 0 && !"PROCESS_TEXT".equals(kind);
    }
}
