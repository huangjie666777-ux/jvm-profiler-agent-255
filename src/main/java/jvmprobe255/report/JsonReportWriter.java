package jvmprobe255.report;

import jvmprobe255.MethodKey;
import jvmprobe255.MethodStats;
import jvmprobe255.Snapshot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes a snapshot as a deterministic UTF-8 JSON report. Only completed
 * invocation statistics are present; calls still in flight are never counted.
 * The file is written atomically via a sibling temporary file. Output failure
 * is surfaced as {@link IOException} so the agent can report it explicitly.
 */
public final class JsonReportWriter {

    private JsonReportWriter() {
    }

    public static void write(Path target, Snapshot snapshot) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.toAbsolutePath().resolveSibling(
                "." + target.getFileName() + "." + ProcessHandle.current().pid() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            render(writer, snapshot);
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void render(BufferedWriter writer, Snapshot snapshot) throws IOException {
        Map<MethodKey, MethodStats> ordered = new TreeMap<>(
                (a, b) -> {
                    int byClass = a.className().compareTo(b.className());
                    if (byClass != 0) {
                        return byClass;
                    }
                    int byMethod = a.methodName().compareTo(b.methodName());
                    if (byMethod != 0) {
                        return byMethod;
                    }
                    return a.descriptor().compareTo(b.descriptor());
                });
        ordered.putAll(snapshot.stats());

        writer.write("{\n  \"methods\": [");
        boolean first = true;
        for (Map.Entry<MethodKey, MethodStats> entry : ordered.entrySet()) {
            MethodKey key = entry.getKey();
            MethodStats stats = entry.getValue();
            if (!first) {
                writer.write(',');
            }
            first = false;
            writer.write("\n    {");
            writer.write("\n      \"className\": ");
            writeString(writer, key.className());
            writer.write(",\n      \"methodName\": ");
            writeString(writer, key.methodName());
            writer.write(",\n      \"descriptor\": ");
            writeString(writer, key.descriptor());
            writer.write(",\n      \"completedCount\": ");
            writer.write(Long.toString(stats.completedCount()));
            writer.write(",\n      \"exceptionCount\": ");
            writer.write(Long.toString(stats.exceptionCount()));
            writer.write(",\n      \"totalInclusiveNanos\": ");
            writer.write(Long.toString(stats.totalInclusiveNanos()));
            writer.write(",\n      \"totalSelfNanos\": ");
            writer.write(Long.toString(stats.totalSelfNanos()));
            writer.write(",\n      \"maxInclusiveNanos\": ");
            writer.write(Long.toString(stats.maxInclusiveNanos()));
            writer.write("\n    }");
        }
        if (!ordered.isEmpty()) {
            writer.write('\n');
            writer.write("  ");
        }
        writer.write("]\n}\n");
    }

    private static void writeString(BufferedWriter writer, String value) throws IOException {
        writer.write('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> writer.write("\\\"");
                case '\\' -> writer.write("\\\\");
                case '\b' -> writer.write("\\b");
                case '\f' -> writer.write("\\f");
                case '\n' -> writer.write("\\n");
                case '\r' -> writer.write("\\r");
                case '\t' -> writer.write("\\t");
                default -> {
                    if (c < 0x20) {
                        writer.write(String.format("\\u%04x", (int) c));
                    } else {
                        writer.write(c);
                    }
                }
            }
        }
        writer.write('"');
    }
}
