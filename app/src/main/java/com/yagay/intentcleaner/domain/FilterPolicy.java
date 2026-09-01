package com.yagay.intentcleaner.domain;

/** Pure policy, independent of Android and covered by the host-side regression harness. */
public final class FilterPolicy {
    private FilterPolicy() {}

    public static boolean sameCaller(int callerUid, int targetUid) {
        return callerUid >= 0 && callerUid == targetUid;
    }

    /** Applies only AFTER a PM query without disabled-component match flags.
     * Manifest enabled defaults and this manager's permissions must not veto a match.
     * Never enables or launches an activity; non-exported foreign targets remain private.
     */
    public static boolean catalogRestricted(boolean exported, int targetUid, int managerUid) {
        return !exported && !sameCaller(managerUid, targetUid);
    }

    public static boolean restoreEmpty(String kind, int before, int after) {
        // Text actions are optional menu entries, not a mandatory file-open destination.
        return before > 0 && after == 0 && !"PROCESS_TEXT".equals(kind);
    }
}
