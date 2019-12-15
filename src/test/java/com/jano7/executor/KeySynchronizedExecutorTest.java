/*
MIT License

Copyright (c) 2019 Jan Gaspar

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.jano7.executor;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class KeySynchronizedExecutorTest {

    private static final int THREAD_COUNT = 10;
    private final ExecutorService underlyingExecutor = Executors.newFixedThreadPool(THREAD_COUNT);
    private final KeySynchronizedExecutor<String> executor = new KeySynchronizedExecutor<>(underlyingExecutor);
    private final Map<String, Long> threadIdMap = Collections.synchronizedMap(new HashMap<>());

    @After
    public void shutDown() throws InterruptedException {
        underlyingExecutor.shutdown();
        underlyingExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    @Test(timeout = 5000)
    public void performCommandsOnSingleThread() throws InterruptedException {
        final CountDownLatch latch1 = new CountDownLatch(2);
        final CountDownLatch latch2 = new CountDownLatch(2);

        executor.execute("sameKey", () -> run(latch1, threadIdMap, "t1"));

        executor.execute(new KeyRunnable<String>() {

            @Override
            public String getKey() {
                return "sameKey";
            }

            @Override
            public void run() {
                KeySynchronizedExecutorTest.run(latch2, threadIdMap, "t2");
            }
        });

        latch1.await(1, TimeUnit.SECONDS);
        latch1.countDown();
        latch1.await();
        latch2.countDown();
        latch2.await();

        assertNotNull(threadIdMap.get("t1"));
        assertNotNull(threadIdMap.get("t2"));
        assertEquals(threadIdMap.get("t1"), threadIdMap.get("t2"));
    }

    @Test(timeout = 5000)
    public void performCommandsInParallel() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(2);

        executor.execute("key1", () -> run(latch, threadIdMap, "t1"));

        executor.execute("aDifferentKey", () -> run(latch, threadIdMap, "t2"));

        latch.await();

        assertNotNull(threadIdMap.get("t1"));
        assertNotNull(threadIdMap.get("t2"));
        assertNotEquals(threadIdMap.get("t1"), threadIdMap.get("t2"));
    }

    @Test(timeout = 5000)
    public void noMemoryLeak() throws
            NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InterruptedException {
        Field keyRunners = KeySynchronizedExecutor.class.getDeclaredField("keyRunners");
        keyRunners.setAccessible(true);
        synchronized (executor) {
            assertTrue(((Map<?, ?>) keyRunners.get(executor)).isEmpty());
        }

        final CountDownLatch latch = new CountDownLatch(THREAD_COUNT / 2);
        for (int i = THREAD_COUNT / 2; i > 0; --i) {
            executor.execute(Integer.toString(i), () -> run(latch, threadIdMap, "t"));
        }

        latch.await();

        synchronized (executor) {
            assertEquals(THREAD_COUNT / 2, ((Map<?, ?>) keyRunners.get(executor)).size());
        }

        Thread.sleep(1000);

        executor.execute("key", () -> run(latch, threadIdMap, "t"));

        synchronized (executor) {
            assertEquals(1, ((Map<?, ?>) keyRunners.get(executor)).size());
        }
    }

    private static void run(CountDownLatch latch, Map<String, Long> threadIdMap, String threadId) {
        threadIdMap.put(threadId, Thread.currentThread().getId());
        latch.countDown();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
    }
}
