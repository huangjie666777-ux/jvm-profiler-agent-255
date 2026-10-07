package demo255;

import java.util.concurrent.CountDownLatch;

/**
 * Plain Java 17 business class exercised in transformed form by the self-tests.
 * It deliberately exercises recursion, mutual calls, caught/uncaught exceptions,
 * catch/finally, overloads, synchronized methods and uninstrumented work.
 */
public class Demo {

    public long factorial(int n) {
        if (n <= 1) {
            return 1L;
        }
        long child = factorial(n - 1);
        return n * child;
    }

    public int mutualEven(int n) {
        if (n == 0) {
            return 1;
        }
        return mutualOdd(n - 1);
    }

    public int mutualOdd(int n) {
        if (n == 0) {
            return 0;
        }
        return mutualEven(n - 1);
    }

    // Overloaded on parameters; same name, different JVM descriptor.
    public String describe(int value) {
        return "int:" + value;
    }

    public String describe(String value) {
        return "str:" + value;
    }

    /** Exception escapes the method -> exceptional exit. */
    public int riskyEscape(int input) {
        if (input < 0) {
            throw new IllegalStateException("boom:" + input);
        }
        return input * 2;
    }

    /** Exception thrown and caught inside -> normal exit, finally still runs. */
    public int riskyCaught(int input) {
        int result;
        try {
            if (input < 0) {
                throw new IllegalArgumentException("caught");
            }
            result = input;
        } catch (IllegalArgumentException e) {
            result = 42;
        } finally {
            touchFinally();
        }
        return result;
    }

    private void touchFinally() {
        // Intentionally empty: presence proves finally semantics survive.
    }

    public static int staticWork(int rounds) {
        int sum = 0;
        for (int i = 0; i < rounds; i++) {
            sum += i;
        }
        return sum;
    }

    public synchronized long synchronizedWork(long millis) {
        long start = System.nanoTime();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return System.nanoTime() - start;
    }

    /** Uninstrumented callees (Thread.sleep) must remain part of self time. */
    public void waitAWhile(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void runWithLatch(CountDownLatch ready, CountDownLatch release) throws InterruptedException {
        ready.countDown();
        release.await();
        factorial(3);
    }
}
