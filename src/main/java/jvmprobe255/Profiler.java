package jvmprobe255;

import jvmprobe255.runtime.ProbeRuntime;
import jvmprobe255.transform.BytecodeTransformer;

/**
 * Public entry point of the profiling SDK.
 *
 * <p>{@link #transform(byte[], ClassLoader)} consumes Java 17 class bytes and
 * returns instrumented bytes without defining or executing the class. The
 * returned class is loaded with the supplied {@link ClassLoader} so that stack
 * map frames can be computed against the application's type hierarchy.</p>
 */
public final class Profiler {
    private final BytecodeTransformer transformer = new BytecodeTransformer();

    /**
     * Instruments a class file.
     *
     * @param classBytes original class bytes
     * @param loader     class loader used to resolve referenced types during frame
     *                   computation; the class itself is never loaded or executed
     * @return instrumented bytes; unchanged when the class has no eligible method
     * @throws IllegalArgumentException if the bytes are not a valid Java 17 class
     *                                  or use an unsupported class form
     */
    public byte[] transform(byte[] classBytes, ClassLoader loader) {
        return transformer.transform(classBytes, loader);
    }

    /** Consistent immutable snapshot of completed invocations only. */
    public static Snapshot snapshot() {
        return ProbeRuntime.snapshot();
    }

    /**
     * Resets all counters.
     *
     * @return {@code true} when cleared, {@code false} when at least one invocation
     *         is in flight (then nothing is cleared)
     */
    public static boolean clear() {
        return ProbeRuntime.clear();
    }

    /** Number of currently active (entered but not exited) instrumented calls. */
    public static long inFlightCount() {
        return ProbeRuntime.inFlightCount();
    }
}
