package jvmprobe255.runtime;

/**
 * One active invocation on the per-thread call stack.
 */
final class Frame {
    final StatsCell cell;
    final long startNanos;
    /** Summed inclusive duration of direct instrumented callees already settled. */
    long childInclusiveNanos;
    /** Direct parent frame, or {@code null} at stack bottom. */
    final Frame parent;

    Frame(StatsCell cell, long startNanos, Frame parent) {
        this.cell = cell;
        this.startNanos = startNanos;
        this.parent = parent;
    }
}
