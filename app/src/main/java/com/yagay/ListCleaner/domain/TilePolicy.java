package com.yagay.ListCleaner.domain;

import java.util.Set;

/** Exact tile identities only. Unknown identities and current tiles always survive. */
public final class TilePolicy {
    private TilePolicy() {}
    public static String canonical(String value) {
        if (value == null || value.length() > 1024) return null;
        String spec = value.trim();
        if (spec.startsWith("custom(") && spec.endsWith(")")) {
            String component = spec.substring(7, spec.length() - 1);
            String[] parts = component.split("/", -1);
            if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") ||
                    !parts[1].matches("[A-Za-z0-9_.$]+")) return null;
            String cls = parts[1].startsWith(".") ? parts[0] + parts[1] : parts[1];
            return "custom(" + parts[0] + "/" + cls + ")";
        }
        return spec.matches("[A-Za-z0-9_][A-Za-z0-9_.:-]*") ? spec : null;
    }
    public static boolean keep(String spec, boolean current, Set<String> hidden) {
        String id = canonical(spec);
        return current || id == null || !hidden.contains(id);
    }
}
