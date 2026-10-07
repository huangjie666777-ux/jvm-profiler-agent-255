package jvmprobe255.agent;

import jvmprobe255.Profiler;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Bridges the JVM instrumentation API to the SDK's {@link Profiler#transform}.
 *
 * <p>Only plain classpath applications are supported: classes loaded by any
 * loader other than the system class loader (including the bootstrap loader,
 * i.e. all JDK classes) are ignored. JDK, ASM and SDK classes are always
 * skipped. A class that fails to transform keeps its original bytes; the
 * failure is reported to stderr and profiling continues for other classes.</p>
 */
public final class AgentClassFileTransformer implements ClassFileTransformer {
    private static final String[] ALWAYS_SKIPPED = {
            "java/", "javax/", "jdk/", "sun/", "com/sun/",
            "org/objectweb/asm/", "jvmprobe255/"
    };

    private final Profiler profiler = new Profiler();
    private final AgentConfig config;

    public AgentClassFileTransformer(AgentConfig config) {
        this.config = config;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null) {
            return null;
        }
        // Plain classpath apps only: the system class loader loads the application.
        if (loader != ClassLoader.getSystemClassLoader()) {
            return null;
        }
        for (String skipped : ALWAYS_SKIPPED) {
            if (className.startsWith(skipped)) {
                return null;
            }
        }
        if (!config.includes(className)) {
            return null;
        }
        try {
            byte[] out = profiler.transform(classfileBuffer, loader);
            return out == classfileBuffer ? null : out;
        } catch (RuntimeException e) {
            System.err.println("[jvmprobe255] skipping class " + className.replace('/', '.')
                    + ": " + e.getMessage());
            return null;
        }
    }
}

