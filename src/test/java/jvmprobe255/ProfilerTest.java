package jvmprobe255;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfilerTest {

    private TransformingLoader loader;
    private Class<?> demoClass;

    private static String desc(Class<?> returnType, Class<?>... params) {
        return org.objectweb.asm.Type.getMethodDescriptor(
                org.objectweb.asm.Type.getType(returnType),
                java.util.Arrays.stream(params)
                        .map(org.objectweb.asm.Type::getType)
                        .toArray(org.objectweb.asm.Type[]::new));
    }

    private static MethodStats stats(Class<?> clazz, String method, String descriptor) {
        return Profiler.snapshot().get(clazz.getName().replace('.', '/'), method, descriptor);
    }

    @BeforeEach
    void setUp() throws Exception {
        Profiler.clear();
        loader = new TransformingLoader(getClass().getClassLoader());
        demoClass = loader.loadTransformed("demo255.Demo");
    }

    private Object newDemo() throws Exception {
        Constructor<?> ctor = demoClass.getConstructor();
        return ctor.newInstance();
    }

    @Test
    void recursionIsSettledPerFrame() throws Exception {
        Object demo = newDemo();
        Method factorial = demoClass.getMethod("factorial", int.class);

        assertEquals(120L, factorial.invoke(demo, 5));

        String descriptor = desc(long.class, int.class);
        MethodStats s = stats(demoClass, "factorial", descriptor);
        assertNotNull(s);
        assertEquals(5L, s.completedCount());
        assertEquals(0L, s.exceptionCount());
        assertTrue(s.totalInclusiveNanos() >= s.totalSelfNanos());
        assertTrue(s.maxInclusiveNanos() >= 0L);
        assertEquals(0, Profiler.inFlightCount());
    }

    @Test
    void mutualRecursionAndOverloadsAreKeyedByDescriptor() throws Exception {
        Object demo = newDemo();
        assertEquals(1, demoClass.getMethod("mutualEven", int.class).invoke(demo, 4));
        assertEquals(1, demoClass.getMethod("mutualOdd", int.class).invoke(demo, 3));
        assertEquals("int:7", demoClass.getMethod("describe", int.class).invoke(demo, 7));
        assertEquals("str:x", demoClass.getMethod("describe", String.class).invoke(demo, "x"));

        MethodStats even = stats(demoClass, "mutualEven", desc(int.class, int.class));
        MethodStats odd = stats(demoClass, "mutualOdd", desc(int.class, int.class));
        MethodStats describeInt = stats(demoClass, "describe", desc(String.class, int.class));
        MethodStats describeString = stats(demoClass, "describe", desc(String.class, String.class));

        assertEquals(5L, even.completedCount());
        assertEquals(4L, odd.completedCount());
        assertEquals(1L, describeInt.completedCount());
        assertEquals(1L, describeString.completedCount());
    }

    @Test
    void escapingExceptionCountsOnceAndCaughtExceptionIsNormal() throws Exception {
        Object demo = newDemo();
        Method riskyEscape = demoClass.getMethod("riskyEscape", int.class);
        Method riskyCaught = demoClass.getMethod("riskyCaught", int.class);

        assertEquals(8, riskyEscape.invoke(demo, 4));
        Exception thrown = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> riskyEscape.invoke(demo, -1));
        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertEquals(42, riskyCaught.invoke(demo, -1));
        assertEquals(7, riskyCaught.invoke(demo, 7));

        MethodStats escape = stats(demoClass, "riskyEscape", desc(int.class, int.class));
        MethodStats caught = stats(demoClass, "riskyCaught", desc(int.class, int.class));

        assertEquals(2L, escape.completedCount());
        assertEquals(1L, escape.exceptionCount());
        assertEquals(2L, caught.completedCount());
        assertEquals(0L, caught.exceptionCount());
        assertEquals(0, Profiler.inFlightCount());
    }

    @Test
    void staticAndSynchronizedMethodsKeepSemanticsAndIncludeWaiting() throws Exception {
        Object demo = newDemo();
        Method staticWork = demoClass.getMethod("staticWork", int.class);
        Method synchronizedWork = demoClass.getMethod("synchronizedWork", long.class);

        assertEquals(45, staticWork.invoke(null, 10));
        long elapsed = (Long) synchronizedWork.invoke(demo, 20L);
        assertTrue(elapsed >= 15_000_000L, "synchronized body should include sleep, was " + elapsed);

        MethodStats syncStats = stats(demoClass, "synchronizedWork", desc(long.class, long.class));
        assertEquals(1L, syncStats.completedCount());
        assertTrue(syncStats.totalInclusiveNanos() >= 15_000_000L);
    }

    @Test
    void multiThreadedCallsDoNotMixStacksOrCounts() throws Exception {
        int threads = 8;
        int iterations = 25;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    Object localDemo = newDemo();
                    Method factorial = demoClass.getMethod("factorial", int.class);
                    for (int i = 0; i < iterations; i++) {
                        assertEquals(24L, factorial.invoke(localDemo, 4));
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }
        start.countDown();
        assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }

        MethodStats s = stats(demoClass, "factorial", desc(long.class, int.class));
        assertEquals((long) threads * iterations * 4L, s.completedCount());
        assertEquals(0L, s.exceptionCount());
        assertEquals(0, Profiler.inFlightCount());
    }

    @Test
    void clearIsRefusedWhileCallIsInFlightAndAllowedAfter() throws Exception {
        Object demo = newDemo();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Method run = demoClass.getMethod("runWithLatch", CountDownLatch.class, CountDownLatch.class);

        Thread worker = new Thread(() -> {
            try {
                run.invoke(demo, ready, release);
            } catch (Exception ignored) {
            }
        });
        worker.start();
        assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));

        assertTrue(Profiler.inFlightCount() > 0);
        assertFalse(Profiler.clear());

        release.countDown();
        worker.join(10_000);
        assertEquals(0, Profiler.inFlightCount());
        assertTrue(Profiler.clear());
        MethodStats after = stats(demoClass, "runWithLatch",
                desc(void.class, CountDownLatch.class, CountDownLatch.class));
        // Counters may exist after clear but must have been reset to zero.
        if (after != null) {
            assertEquals(0L, after.completedCount());
        }
    }

    @Test
    void retransformingOutputAddsNoExtraProbes() throws Exception {
        Profiler profiler = new Profiler();
        byte[] original = readResource("demo255/Demo.class");
        byte[] once = profiler.transform(original, loader);
        byte[] twice = profiler.transform(once, loader);
        assertSame(once, twice, "second transform must return already-instrumented bytes");

        Object demo = newDemo();
        demoClass.getMethod("factorial", int.class).invoke(demo, 3);
        MethodStats s = stats(demoClass, "factorial", desc(long.class, int.class));
        assertEquals(3L, s.completedCount(), "must not double-count probes");
    }

    @Test
    void constructorsAreSkippedAndIllegalAndUnsupportedClassesAreRejected() throws Exception {
        Profiler profiler = new Profiler();
        byte[] constructorClass = readResource("demo255/ConstructorsOnly.class");
        assertSame(constructorClass,
                profiler.transform(constructorClass, getClass().getClassLoader()));

        byte[] interfaceBytes = readResource("demo255/DemoAPI.class");
        assertThrows(IllegalArgumentException.class,
                () -> profiler.transform(interfaceBytes, getClass().getClassLoader()));

        assertThrows(IllegalArgumentException.class,
                () -> profiler.transform(new byte[]{1, 2, 3}, getClass().getClassLoader()));
        byte[] badMagic = new byte[100];
        badMagic[0] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> profiler.transform(badMagic, getClass().getClassLoader()));
    }

    @Test
    void sdkClassesAreNeverInstrumented() {
        Profiler profiler = new Profiler();
        byte[] sdkBytes = readResource("jvmprobe255/Profiler.class");
        assertSame(sdkBytes, profiler.transform(sdkBytes, Profiler.class.getClassLoader()));
    }

    @Test
    void successfulClearDropsAllOldCounters() throws Exception {
        Object demo = newDemo();
        demoClass.getMethod("factorial", int.class).invoke(demo, 4);
        assertNotNull(stats(demoClass, "factorial", desc(long.class, int.class)));

        assertEquals(0, Profiler.inFlightCount());
        assertTrue(Profiler.clear());
        // A later snapshot must be a clean registry: no reset-but-present old entries.
        assertTrue(Profiler.snapshot().stats().isEmpty());

        demoClass.getMethod("mutualEven", int.class).invoke(demo, 2);
        Snapshot snapshot = Profiler.snapshot();
        assertEquals(2L,
                snapshot.get(demoClass.getName().replace('.', '/'), "mutualEven",
                        desc(int.class, int.class)).completedCount());
        assertNull(snapshot.get(demoClass.getName().replace('.', '/'), "factorial",
                desc(long.class, int.class)));
    }

    private static byte[] readResource(String resource) {
        String classResource = resource.endsWith(".class") ? resource : resource + ".class";
        try (java.io.InputStream in = ProfilerTest.class.getClassLoader()
                .getResourceAsStream(classResource)) {
            assertNotNull(in, "missing test resource " + classResource);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
