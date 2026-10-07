package jvmprobe255.report;

import jvmprobe255.MethodKey;
import jvmprobe255.MethodStats;
import jvmprobe255.Snapshot;
import jvmprobe255.runtime.ProbeRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReportWriterTest {

    @Test
    void writesUtf8JsonWithAllCompletedStats(@TempDir Path dir) throws Exception {
        Map<MethodKey, MethodStats> stats = new LinkedHashMap<>();
        stats.put(new MethodKey("com/acme/Foo", "run", "()V"),
                new MethodStats(3, 1, 1_000L, 700L, 400L));
        stats.put(new MethodKey("com/acme/中/Bar", "greet", "()Ljava/lang/String;"),
                new MethodStats(1, 0, 10L, 10L, 10L));

        Path target = dir.resolve("nested/report.json");
        JsonReportWriter.write(target, new Snapshot(stats));

        String json = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\n"));
        assertTrue(json.contains("\"className\": \"com/acme/Foo\""));
        assertTrue(json.contains("\"methodName\": \"run\""));
        assertTrue(json.contains("\"descriptor\": \"()V\""));
        assertTrue(json.contains("\"completedCount\": 3"));
        assertTrue(json.contains("\"exceptionCount\": 1"));
        assertTrue(json.contains("\"totalInclusiveNanos\": 1000"));
        assertTrue(json.contains("\"totalSelfNanos\": 700"));
        assertTrue(json.contains("\"maxInclusiveNanos\": 400"));
        assertTrue(json.contains("com/acme/中/Bar"));
    }

    @Test
    void writeFailureIsReportedAsIoException(@TempDir Path dir) throws Exception {
        Path blocked = dir.resolve("not-a-dir");
        Files.writeString(blocked, "x");
        Path underBlocked = blocked.resolve("report.json");
        assertThrows(java.io.IOException.class,
                () -> JsonReportWriter.write(underBlocked, ProbeRuntime.snapshot()));
        assertEquals(0L, ProbeRuntime.inFlightCount());
    }
}
