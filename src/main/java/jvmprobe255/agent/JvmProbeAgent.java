package jvmprobe255.agent;

import jvmprobe255.Profiler;
import jvmprobe255.report.JsonReportWriter;

import java.lang.instrument.Instrumentation;

/**
 * Startup-time Java agent entry point. Adding
 * {@code -javaagent:jvmprobe255-0.1.0.jar=packages=...;report=...} to the
 * application {@code java} command is enough; no business source changes and
 * no manual {@code Profiler.transform} call are needed. The manual SDK API
 * keeps working unchanged.
 */
public final class JvmProbeAgent {

    private JvmProbeAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        // Illegal arguments throw here: the JVM reports the failure before main.
        AgentOptions options = AgentOptions.parse(agentArgs);

        instrumentation.addTransformer(
                new ProfilingTransformer(options.newFilter()), false);

        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> writeReport(options), "jvmprobe255-report"));
    }

    private static void writeReport(AgentOptions options) {
        try {
            // Snapshot covers completed invocations only; calls still in flight
            // are excluded by definition and are never backfilled.
            JsonReportWriter.write(options.reportPath(), Profiler.snapshot());
        } catch (Throwable t) {
            System.err.println("[jvmprobe255] failed to write report to "
                    + options.reportPath() + ": " + t);
            Runtime.getRuntime().halt(1);
        }
    }
}
