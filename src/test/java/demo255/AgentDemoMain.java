package demo255;

import demo255.excluded.Hidden;

/**
 * Plain sample application for the Java agent demo. It never touches the SDK:
 * profiling happens purely through {@code -javaagent} bytecode transformation.
 */
public final class AgentDemoMain {

    public static void main(String[] args) {
        AgentDemoMain app = new AgentDemoMain();
        System.out.println("fib(12) = " + app.fib(12));
        app.busyWork(20);
        Hidden.run();
        System.out.println("agent demo finished");
    }

    public long fib(int n) {
        if (n < 2) {
            return n;
        }
        return fib(n - 1) + fib(n - 2);
    }

    public void busyWork(long millis) {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        long sink = 0;
        while (System.nanoTime() < deadline) {
            sink += System.nanoTime() & 1L;
        }
        if (sink == Long.MIN_VALUE) {
            System.out.println("unreachable");
        }
    }
}

