package jvmprobe255.agent;

import jvmprobe255.Profiler;
import jvmprobe255.Snapshot;
import jvmprobe255.runtime.ExitGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {

    @BeforeEach
    void reset() {
        Profiler.clear();
    }

    @Test
    void reportContainsMethodIdentityAndAllStats() throws Exception {
        long token = ExitGuard.safeEnter("com/acme/Widget", "render", "(I)Ljava/lang/String;");
        ExitGuard.safeExit(token, false);
        long failing = ExitGuard.safeEnter("com/acme/Widget", "render", "(I)Ljava/lang/String;");
        ExitGuard.safeExit(failing, true);

        Snapshot snapshot = Profiler.snapshot();
        String json = ReportWriter.toJson(snapshot);
        assertTrue(json.contains("\"className\": \"com/acme/Widget\""));
        assertTrue(json.contains("\"methodName\": \"render\""));
        assertTrue(json.contains("\"descriptor\": \"(I)Ljava/lang/String;\""));
        assertTrue(json.contains("\"completedCount\": 2"));
        assertTrue(json.contains("\"exceptionCount\": 1"));
        assertTrue(json.contains("totalInclusiveNanos"));
        assertTrue(json.contains("totalSelfNanos"));
        assertTrue(json.contains("maxInclusiveNanos"));
    }

    @Test
    void reportIsWrittenAsUtf8JsonAndInFlightCallsAreNotCompleted() throws Exception {
        long token = ExitGuard.safeEnter("com/acme/Worker", "run", "()V");
        ExitGuard.safeExit(token, false);
        long stuck = ExitGuard.safeEnter("com/acme/Worker", "stuck", "()V"); // still in flight

        Path report = Files.createTempDirectory("probe-report").resolve("sub/report.json");
        ReportWriter.write(report.toString());

        String json = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"completedCount\": 1"), json);
        assertTrue(json.contains("\"inFlightAtShutdown\": 1"), json);
        // The stuck method never completed, so it must not appear as a completed entry.
        assertTrue(!json.contains("stuck"), json);
        // Settle the in-flight frame so later tests see a quiet runtime.
        ExitGuard.safeExit(stuck, false);
        assertEquals(0, Profiler.inFlightCount());
        assertTrue(Profiler.clear());
    }

    @Test
    void unwritableReportPathReportsErrorWithoutThrowing() {
        ReportWriter.write("/nonexistent-dir-\u0000x/report.json");
    }
}
