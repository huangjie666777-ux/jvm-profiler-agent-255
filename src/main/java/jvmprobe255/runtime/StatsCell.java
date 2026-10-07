package jvmprobe255.runtime;

import jvmprobe255.MethodStats;

/**
 * Mutable per-method counters. All access is guarded by the cell instance itself,
 * which keeps a concurrent snapshot consistent per method without a global hot lock.
 */
final class StatsCell {
    long completedCount;
    long exceptionCount;
    long totalInclusiveNanos;
    long totalSelfNanos;
    long maxInclusiveNanos;

    MethodStats snapshot() {
        return new MethodStats(completedCount, exceptionCount, totalInclusiveNanos,
                totalSelfNanos, maxInclusiveNanos);
    }

    void reset() {
        completedCount = 0L;
        exceptionCount = 0L;
        totalInclusiveNanos = 0L;
        totalSelfNanos = 0L;
        maxInclusiveNanos = 0L;
    }
}
