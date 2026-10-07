package jvmprobe255.runtime;

/**
 * Defense boundary for probe bookkeeping. Profiling must never change business
 * control flow: any failure of the probe is swallowed, and the original
 * return value / exception semantics are preserved by the instrumented code.
 */
public final class ExitGuard {
    private ExitGuard() {
    }

    public static long safeEnter(String className, String methodName, String descriptor) {
        try {
            return ProbeRuntime.enter(className, methodName, descriptor);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static void safeExit(long token, boolean exceptional) {
        if (token == 0L) {
            return;
        }
        try {
            ProbeRuntime.exit(token, exceptional);
        } catch (Throwable ignored) {
            // The business method has already produced its result/exception;
            // profiling failure must never replace it.
        }
    }
}
