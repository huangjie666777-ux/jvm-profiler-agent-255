package jvmprobe255.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java agent entry point. Attach with:
 *
 * <pre>{@code
 * java -javaagent:jvmprobe255-agent.jar=include=com.example,report=probe.json -jar app.jar
 * }</pre>
 *
 * <p>Invalid arguments abort the JVM before {@code main} runs. A shutdown hook
 * writes the UTF-8 JSON report on normal JVM exit.</p>
 */
public final class ProbeAgent {

    private ProbeAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        AgentConfig config;
        try {
            config = AgentConfig.parse(agentArgs);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("[jvmprobe255] " + e.getMessage(), e);
        }
        instrumentation.addTransformer(new AgentClassFileTransformer(config));
        String reportPath = config.reportPath();
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> ReportWriter.write(reportPath), "jvmprobe255-report"));
    }
}

