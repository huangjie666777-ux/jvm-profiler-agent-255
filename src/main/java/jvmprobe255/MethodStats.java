package jvmprobe255;

/**
 * Immutable statistics for one method captured at a point in time.
 */
public final class MethodStats {
    private final long completedCount;
    private final long exceptionCount;
    private final long totalInclusiveNanos;
    private final long totalSelfNanos;
    private final long maxInclusiveNanos;

    public MethodStats(long completedCount, long exceptionCount, long totalInclusiveNanos,
                       long totalSelfNanos, long maxInclusiveNanos) {
        this.completedCount = completedCount;
        this.exceptionCount = exceptionCount;
        this.totalInclusiveNanos = totalInclusiveNanos;
        this.totalSelfNanos = totalSelfNanos;
        this.maxInclusiveNanos = maxInclusiveNanos;
    }

    /** Number of completed invocations (normal return or escaping exception). */
    public long completedCount() {
        return completedCount;
    }

    /** Invocations that exited with an exception escaping the method body. */
    public long exceptionCount() {
        return exceptionCount;
    }

    /** Sum of wall time from entry to exit, including instrumented callees and waiting. */
    public long totalInclusiveNanos() {
        return totalInclusiveNanos;
    }

    /** Sum of inclusive time minus the inclusive time of direct instrumented callees. */
    public long totalSelfNanos() {
        return totalSelfNanos;
    }

    public long maxInclusiveNanos() {
        return maxInclusiveNanos;
    }

    @Override
    public String toString() {
        return "MethodStats{completed=" + completedCount
                + ", exceptions=" + exceptionCount
                + ", inclusiveNs=" + totalInclusiveNanos
                + ", selfNs=" + totalSelfNanos
                + ", maxInclusiveNs=" + maxInclusiveNanos + '}';
    }
}
