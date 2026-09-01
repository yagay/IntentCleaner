import com.yagay.intentcleaner.ui.DiagnosticEvidence;

public final class DiagnosticEvidenceCheck {
    public static void main(String[] args) throws Exception {
        DiagnosticEvidence evidence = new DiagnosticEvidence();
        String prefix = "[time] [com.yagay.intentcleaner,Intentcleaner.Diagnostic,x] pid=42 process=system ";
        evidence.accept("verbose", prefix + "QUERY layer=SYSTEM kind=OPEN");
        evidence.accept("modules", prefix + "QUERY layer=SYSTEM kind=OPEN");
        evidence.accept("modules", prefix + "MANAGER_QUERY_BYPASS uid=10715");
        evidence.accept("modules", "unrelated private log");
        String report = evidence.report();
        if (!report.contains("duplicatesRemoved=1") || !report.contains("queryObserved kind=OPEN events=1") ||
            !report.contains("managerBypass events=1") || report.contains("unrelated private log")) throw new AssertionError(report);
        for (int i=0; i<8300; i++) evidence.accept("test", prefix + "QUERY kind=OPEN n=" + i);
        report = evidence.report();
        if (!report.contains("omittedEvents=110") || !report.contains("textTruncated=true")) throw new AssertionError("bounds");
        if (args.length == 2) {
            DiagnosticEvidence real = new DiagnosticEvidence();
            for (String path : args) {
                try (java.util.stream.Stream<String> lines = java.nio.file.Files.lines(java.nio.file.Path.of(path))) {
                    lines.forEach(line -> real.accept(java.nio.file.Path.of(path).getFileName().toString(), line));
                }
            }
            System.out.println(real.report().split("Evidence:")[0]);
        }
        System.out.println("PASS: deduplication, counters, unrelated exclusion, event and byte bounds");
    }
}
