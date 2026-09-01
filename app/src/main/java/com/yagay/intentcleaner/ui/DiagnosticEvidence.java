package com.yagay.intentcleaner.ui;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, deduplicated historical evidence. Counts are observations, not live health. */
public final class DiagnosticEvidence {
    private static final Pattern ID = Pattern.compile("pid=(\\d+) process=(\\S+)");
    private static final Pattern KIND = Pattern.compile("kind=(\\w+)");
    private final DiagnosticBuffer buffer = new DiagnosticBuffer(512 * 1024);
    private final Set<String> seen = new HashSet<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final MessageDigest digest;
    private int duplicates;
    private int omitted;

    public DiagnosticEvidence() {
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    public void accept(String source, String line) {
        if (!line.contains("com.yagay.intentcleaner,Intentcleaner.Diagnostic,")) return;
        String hash = java.util.Base64.getEncoder().encodeToString(digest.digest(line.getBytes(StandardCharsets.UTF_8)));
        if (seen.contains(hash)) { duplicates++; return; }
        if (seen.size() >= 8192) { omitted++; return; }
        seen.add(hash);
        Matcher id = ID.matcher(line);
        if (id.find()) {
            String stage = line.contains(" MODULE_LOADED ") ? "loaded" :
                line.contains(" HOOK_INSTALLED ") ? "hookInstalled" :
                line.contains(" QUERY ") ? "queryObserved" :
                line.contains(" ORDER_APPLIED ") ? "orderObserved" :
                line.contains(" MANAGER_QUERY_BYPASS ") ? "managerBypass" :
                line.contains("FILTER_PAUSED") ? "identityUnknown" : null;
            if (stage != null) {
                Matcher kind = KIND.matcher(line);
                String key = id.group(2) + " pid=" + id.group(1) + " " + stage +
                    (kind.find() ? " kind=" + kind.group(1) : "");
                if (counts.containsKey(key) || counts.size() < 512) counts.merge(key, 1, Integer::sum);
            }
        }
        byte[] bytes = ("[" + source + "] " + line + "\n").getBytes(StandardCharsets.UTF_8);
        buffer.append(bytes, bytes.length);
    }

    public String report() {
        StringBuilder result = new StringBuilder("Historical observations, NOT live health.\n");
        result.append("Missing evidence is UNKNOWN. Counts are deduplicated event counts, not active hooks.\n")
            .append("Check source timestamps/PIDs; old boots, truncation and rate limits can hide events.\n")
            .append("duplicatesRemoved=").append(duplicates).append(" omittedEvents=").append(omitted)
            .append(" textTruncated=").append(buffer.truncated()).append("\n\n");
        counts.forEach((key, value) -> result.append(key).append(" events=").append(value).append('\n'));
        return result.append("\nEvidence:\n").append(new String(buffer.snapshot(), StandardCharsets.UTF_8)).toString();
    }
}
