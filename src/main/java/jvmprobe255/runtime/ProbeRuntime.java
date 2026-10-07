package jvmprobe255.runtime;

import jvmprobe255.MethodKey;
import jvmprobe255.MethodStats;
import jvmprobe255.Snapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Runtime registry entered by instrumented bytecode.
 *
 * <p>Each thread keeps an independent call stack, so recursion, mutual recursion
 * and cross-thread calls never interfere. A frame is settled exactly once on
 * either normal or exceptional exit; internally caught exceptions never reach
 * the exit path and are therefore counted as normal returns.</p>
 */
public final class ProbeRuntime {
    private static final ConcurrentHashMap<MethodKey, StatsCell> CELLS = new ConcurrentHashMap<>();
    /** Identity tokens never equal 0 so that 0 can mark "probe disabled for this invocation". */
    private static final AtomicLong TOKENS = new AtomicLong(1L);
    private static final ConcurrentHashMap<Long, Stack> STACKS = new ConcurrentHashMap<>();
    /** Number of in-flight calls across all threads. */
    private static final AtomicLong IN_FLIGHT = new AtomicLong();
    /**
     * Single concurrency protocol for the whole registry: completed-count
     * publication (enter/exit) takes the read side, while snapshot/clear take
     * the write side. A snapshot therefore corresponds to one consistent
     * instant, and clear can only observe quiescence when no enter/exit region
     * is active, so old counters can never bleed into post-clear state.
     */
    private static final ReentrantReadWriteLock PROTOCOL_LOCK = new ReentrantReadWriteLock();

    private static final class Stack {
        Frame top;
    }

    private ProbeRuntime() {
    }

    /**
     * Called by instrumented entry code. Returns a nonzero token on success; a zero
     * token means profiling is disabled for this invocation and {@link #exit} is skipped.
     * All bookkeeping failures are contained here and never propagate to business code.
     */
    public static long enter(String className, String methodName, String descriptor) {
        PROTOCOL_LOCK.readLock().lock();
        try {
            MethodKey key = new MethodKey(className, methodName, descriptor);
            StatsCell cell = resolveCell(key);
            long threadId = Thread.currentThread().getId();
            Stack stack = STACKS.computeIfAbsent(threadId, id -> new Stack());
            long token = TOKENS.getAndIncrement();
            long start = System.nanoTime();
            // Publish the frame last; any failure before this point changed no stack.
            IN_FLIGHT.incrementAndGet();
            stack.top = new Frame(cell, start, stack.top);
            return token;
        } catch (Throwable t) {
            return 0L;
        } finally {
            PROTOCOL_LOCK.readLock().unlock();
        }
    }

    private static StatsCell resolveCell(MethodKey key) {
        return CELLS.computeIfAbsent(key, k -> new StatsCell());
    }

    /**
     * Called exactly once per entered invocation, from either the normal return
     * chokepoint or the synthetic outer exception handler.
     *
     * @param token       token produced by {@link #enter}; ignored when zero
     * @param exceptional whether the method exits with an escaping throwable
     */
    public static void exit(long token, boolean exceptional) {
        PROTOCOL_LOCK.readLock().lock();
        try {
            doExit(token, exceptional);
        } finally {
            PROTOCOL_LOCK.readLock().unlock();
        }
    }

    private static void doExit(long token, boolean exceptional) {
        long threadId = Thread.currentThread().getId();
        Stack stack = STACKS.get(threadId);
        if (stack == null || stack.top == null) {
            return;
        }
        Frame frame = stack.top;
        stack.top = frame.parent;
        if (frame.parent == null) {
            STACKS.remove(threadId);
        }
        IN_FLIGHT.decrementAndGet();

        long inclusive = System.nanoTime() - frame.startNanos;
        if (inclusive < 0L) {
            inclusive = 0L;
        }
        long self = inclusive - frame.childInclusiveNanos;
        if (self < 0L) {
            self = 0L;
        }

        if (frame.parent != null) {
            // Only direct instrumented children are subtracted, and each child's
            // inclusive time is added once: grand-children are inside that value.
            frame.parent.childInclusiveNanos += inclusive;
        }

        StatsCell cell = frame.cell;
        synchronized (cell) {
            cell.completedCount++;
            if (exceptional) {
                cell.exceptionCount++;
            }
            cell.totalInclusiveNanos += inclusive;
            cell.totalSelfNanos += self;
            if (inclusive > cell.maxInclusiveNanos) {
                cell.maxInclusiveNanos = inclusive;
            }
        }
    }

    public static Snapshot snapshot() {
        PROTOCOL_LOCK.writeLock().lock();
        try {
            Map<MethodKey, MethodStats> result = new LinkedHashMap<>();
            for (Map.Entry<MethodKey, StatsCell> entry : CELLS.entrySet()) {
                StatsCell cell = entry.getValue();
                synchronized (cell) {
                    result.put(entry.getKey(), cell.snapshot());
                }
            }
            return new Snapshot(result);
        } finally {
            PROTOCOL_LOCK.writeLock().unlock();
        }
    }

    /**
     * Clears all statistics. Only allowed when no instrumented invocation is in flight.
     *
     * @return {@code true} when cleared; {@code false} if calls are currently active
     */
    public static boolean clear() {
        PROTOCOL_LOCK.writeLock().lock();
        try {
            if (IN_FLIGHT.get() != 0L) {
                return false;
            }
            CELLS.clear();
            return true;
        } finally {
            PROTOCOL_LOCK.writeLock().unlock();
        }
    }

    public static long inFlightCount() {
        return IN_FLIGHT.get();
    }

    /** Test/diagnostic helper: depth of the current thread's instrumented call stack. */
    public static int currentDepth() {
        Stack stack = STACKS.get(Thread.currentThread().getId());
        if (stack == null) {
            return 0;
        }
        int depth = 0;
        for (Frame f = stack.top; f != null; f = f.parent) {
            depth++;
        }
        return depth;
    }

    /** Diagnostic helper: keys currently registered, in registration-independent order. */
    public static List<MethodKey> registeredKeys() {
        return new ArrayList<>(CELLS.keySet());
    }
}
