package demo255;

import jvmprobe255.MethodKey;
import jvmprobe255.MethodStats;
import jvmprobe255.Profiler;
import jvmprobe255.Snapshot;
import jvmprobe255.TransformingLoader;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Standalone demo: load {@link Demo} in transformed form, drive recursion,
 * escaping/caught exceptions and multi-threaded calls, then print the snapshot.
 */
public final class DemoMain {
    private DemoMain() {
    }

    public static void main(String[] args) throws Exception {
        TransformingLoader loader = new TransformingLoader(DemoMain.class.getClassLoader());
        Class<?> demoClass = loader.loadTransformed("demo255.Demo");
        Object demo = demoClass.getConstructor().newInstance();
        Method factorial = demoClass.getMethod("factorial", int.class);
        Method risky = demoClass.getMethod("riskyEscape", int.class);
        Method caught = demoClass.getMethod("riskyCaught", int.class);

        System.out.println("factorial(6) = " + factorial.invoke(demo, 6));
        try {
            risky.invoke(demo, -1);
        } catch (java.lang.reflect.InvocationTargetException e) {
            System.out.println("escaping exception preserved: " + e.getCause());
        }
        System.out.println("internally caught exception result = " + caught.invoke(demo, -1));

        int threads = 4;
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final Object local = demoClass.getConstructor().newInstance();
            workers[i] = new Thread(() -> {
                try {
                    for (int round = 0; round < 10; round++) {
                        factorial.invoke(local, 5);
                    }
                } catch (Exception ignored) {
                }
            });
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("\n%-55s %8s %8s %14s %14s %14s".formatted(
                "method", "count", "errors", "inclusive(ns)", "self(ns)", "max(ns)"));
        Snapshot snapshot = Profiler.snapshot();
        snapshot.stats().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(MethodKey::toString)))
                .forEach(DemoMain::printRow);
        System.out.println("\nin-flight after all joins = " + Profiler.inFlightCount());
        System.out.println("clear with no in-flight calls = " + Profiler.clear());
    }

    private static void printRow(Map.Entry<MethodKey, MethodStats> entry) {
        MethodKey key = entry.getKey();
        MethodStats s = entry.getValue();
        String name = key.className() + "." + key.methodName() + key.descriptor();
        if (name.length() > 55) {
            name = name.substring(0, 52) + "...";
        }
        System.out.println("%-55s %8d %8d %14d %14d %14d".formatted(
                name, s.completedCount(), s.exceptionCount(),
                s.totalInclusiveNanos(), s.totalSelfNanos(), s.maxInclusiveNanos()));
    }
}
