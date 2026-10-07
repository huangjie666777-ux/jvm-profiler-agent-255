package jvmprobe255.agent;

import jvmprobe255.MethodKey;
import jvmprobe255.MethodStats;
import jvmprobe255.Profiler;
import jvmprobe255.Snapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Writes the final UTF-8 JSON report on JVM shutdown. Only completed
 * invocations appear; in-flight calls at shutdown are not counted as completed.
 * Any output failure is reported to stderr with the target path.
 */
public final class ReportWriter {

    private ReportWriter() {
    }

    public static void write(String reportPath) {
        Snapshot snapshot = Profiler.snapshot();
        try {
            Path path = Path.of(reportPath);
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(path)) {
                out.write(toJson(snapshot).getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[jvmprobe255] failed to write report to " + reportPath
                    + ": " + e);
        }
    }

    static String toJson(Snapshot snapshot) {
        List<Map.Entry<MethodKey, MethodStats>> entries =
                new ArrayList<>(snapshot.stats().entrySet());
        // Only completed invocations are reported: in-flight calls at shutdown
        // and cells left at zero by a clear contribute no entry.
        entries.removeIf(e -> e.getValue().completedCount() == 0L);
        entries.sort(Comparator.comparing(e -> e.getKey().toString()));
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"inFlightAtShutdown\": ").append(Profiler.inFlightCount()).append(",\n");
        json.append("  \"methods\": [");
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<MethodKey, MethodStats> entry = entries.get(i);
            MethodKey key = entry.getKey();
            MethodStats s = entry.getValue();
            json.append(i == 0 ? "\n" : ",\n");
            json.append("    {");
            json.append("\"className\": \"").append(escape(key.className())).append("\", ");
            json.append("\"methodName\": \"").append(escape(key.methodName())).append("\", ");
            json.append("\"descriptor\": \"").append(escape(key.descriptor())).append("\", ");
            json.append("\"completedCount\": ").append(s.completedCount()).append(", ");
            json.append("\"exceptionCount\": ").append(s.exceptionCount()).append(", ");
            json.append("\"totalInclusiveNanos\": ").append(s.totalInclusiveNanos()).append(", ");
            json.append("\"totalSelfNanos\": ").append(s.totalSelfNanos()).append(", ");
            json.append("\"maxInclusiveNanos\": ").append(s.maxInclusiveNanos());
            json.append("}");
        }
        json.append(entries.isEmpty() ? "]\n" : "\n  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
