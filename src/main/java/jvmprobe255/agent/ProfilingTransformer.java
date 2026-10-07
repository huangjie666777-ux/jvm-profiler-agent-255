package jvmprobe255.agent;

import jvmprobe255.transform.BytecodeTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * {@link ClassFileTransformer} that reuses {@link BytecodeTransformer} and the
 * existing runtime registry. Only classes selected by {@link PackageFilter} are
 * touched; any transformation failure keeps the original class bytes and is
 * reported on stderr so profiling never blocks application startup and other
 * classes keep being collected.
 *
 * <p>This agent supports plain classpath applications loaded by the system
 * class loader only. Dynamic attach, retransformation and named modules are
 * out of scope.</p>
 */
final class ProfilingTransformer implements ClassFileTransformer {
    private final PackageFilter filter;
    private final BytecodeTransformer transformer = new BytecodeTransformer();

    ProfilingTransformer(PackageFilter filter) {
        this.filter = filter;
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (classBeingRedefined != null) {
            return null;
        }
        if (loader != ClassLoader.getSystemClassLoader()) {
            return null;
        }
        if (className == null || !filter.shouldTransform(className)) {
            return null;
        }
        try {
            byte[] transformed = transformer.transform(classfileBuffer, loader);
            return transformed == classfileBuffer ? null : transformed;
        } catch (Throwable t) {
            System.err.println("[jvmprobe255] skipped " + className + ": " + t);
            return null;
        }
    }
}
