package betterthreadpool;

import java.util.Arrays;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * A class used for instantiating a number of {@link Thread Threads} that
 * can be used to execute {@link Runnable} tasks, immediately, at a delay, or repeatedly.
 *
 * A {@code ThreadPool} works by using a {@link Deque} to store {@link ThreadPoolTask}s for execution. Each thread in the pool attempts
 * to grab a task from the head of the queue every loop. If there is an available task that has met the requirements to be executed (the task's delay
 * and the time before a repeated execution has also elapsed, and the task is not cancelled), then the task is executed by the first thread to
 * acquire it, without guarantees as to which thread acquires it. If the task at the head of the queue does not meet the requirements for execution,
 * then the task is placed back into the back of the queue.
 */
public class ThreadPool {
    private Executor[] threads;
    private final Deque<ThreadPoolTask> queue;
    private boolean isClosed;
    private ThreadFactory factory;
    private static final ThreadFactory DEFAULT_FACTORY = new DefaultThreadFactory();
    private static final ThreadPoolTask EMPTY_TASK = new ThreadPoolTask();

    /**
     * Constructs a new {@code ScheduledThreadPool}.
     * @param threadCount The number of threadCount to instantiate the pool with
     */
    public ThreadPool(int threadCount) {
        isClosed = false;
        threads = new Executor[threadCount];
        queue = new ConcurrentLinkedDeque<>();
        factory = DEFAULT_FACTORY;
        populateThreads();
    }

    /**
     * Constructs a new {@code ScheduledThreadPool}.
     * @param threadCount The number of threadCount to instantiate the pool with
     * @param factory The {@code ThreadFactory} to use when instantiating threads
     */
    public ThreadPool(int threadCount, ThreadFactory factory) {
        if(factory == null)
            throw new NullPointerException();
        isClosed = false;
        threads = new Executor[threadCount];
        queue = new ConcurrentLinkedDeque<>();
        this.factory = factory;
        populateThreads();
    }

    /**
     * Schedules a new {@code Runnable} for execution at an interval.
     * @param task The {@code Runnable} to execute
     * @param interval The interval between execution
     * @param delay The delay before initial execution
     * @param unit The {@code TimeUnit} of the interval and delay
     * @return The {@code ThreadPoolTask} created
     */
    public ThreadPoolTask scheduleTask(Runnable task, long interval, long delay, TimeUnit unit) {
        if(task == null || unit == null)
            throw new NullPointerException();
        ThreadPoolTask threadPoolTask = new ThreadPoolTask(task, interval, delay, unit);
        queue.add(threadPoolTask);
        return threadPoolTask;
    }

    /**
     * Schedules a new {@code Runnable} for execution.
     * @param task The {@code Runnable} to execute
     * @param delay The delay before initial execution
     * @param unit The {@code TimeUnit} of the interval and delay
     * @return The {@code ThreadPoolTask} created
     */
    public ThreadPoolTask scheduleTask(Runnable task, long delay, TimeUnit unit) {
        if(task == null || unit == null)
            throw new NullPointerException();
        ThreadPoolTask threadPoolTask = new ThreadPoolTask(task, delay, unit);
        queue.add(threadPoolTask);
        return threadPoolTask;
    }

    /**
     * Submits a task to be executed as soon as possible.
     * @param task The {@code Runnable} to execute
     * @return The {@code ThreadPoolTask} created
     */
    public ThreadPoolTask submit(Runnable task) {
        if(task == null)
            throw new NullPointerException();
        ThreadPoolTask threadPoolTask = new ThreadPoolTask(task, 0, TimeUnit.NANOSECONDS);
        queue.add(threadPoolTask);
        return threadPoolTask;
    }

    /**
     * Submits a task to be executed immediately, placing it at the front of the task queue.
     * @param task The {@code Runnable} to execute
     * @return The {@code ThreadPoolTask} created
     */
    public ThreadPoolTask execute(Runnable task) {
        if(task == null)
            throw new NullPointerException();
        ThreadPoolTask threadPoolTask = new ThreadPoolTask(task, 0, TimeUnit.NANOSECONDS);
        queue.addFirst(threadPoolTask);
        return threadPoolTask;
    }

    private void populateThreads() {
        for(int i = 0; i < threads.length; i++) {
            if(threads[i] == null) {
                threads[i] = new Executor();
            }
        }
    }

    /**
     * Sets the number of threads for the pool to use.
     * Note that changing the number of threads can be an expensive operation and doing it too often defeats the point of a thread pool.
     * @param threadCount The number of threads the pool should use.
     */
    public void setThreadCount(int threadCount) {
        try {
            removeExcessThreads(threadCount);
            threads = Arrays.copyOf(threads, threadCount);
            populateThreads();
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the current number of threads in the pool.
     * @return The number of threads
     */
    public int getThreadCount() {
        return threads.length;
    }

    /**
     * Returns the pool's thread factory.
     * @return The {@code ThreadFactory} currently in use by the pool.
     */
    public ThreadFactory getFactory() {
        return factory;
    }

    /**
     * Sets the thread factory to use when instantiating threads.
     * @param factory The {@code ThreadFactory} to use
     */
    public void setFactory(ThreadFactory factory) {
        if(factory == null)
            throw new NullPointerException();
        this.factory = factory;
    }

    /**
     * Closes the thread pool, blocking until all currently executing tasks are complete and all threads can be safely closed and discarded.
     * Cannot be reopened once closed.
     */
    public void close() {
        try {
            removeExcessThreads(-threads.length);
            isClosed = true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        queue.clear();
    }

    private void removeExcessThreads(int threadCount) throws InterruptedException {
        for(int i = 0; i < threads.length; i++) {
            if(i > threadCount) {
                threads[i].close();
                threads[i] = null;
            }
        }
    }

    private class ExecutionTask implements Runnable {
        private boolean closed;
        public ExecutionTask() {
            closed = false;
        }

        @Override
        public void run() {
            while(!closed) {
                ThreadPoolTask threadPoolTask = queue.poll();
                if (threadPoolTask != null) {
                    if (threadPoolTask.isExecutable())
                        threadPoolTask.execute();
                    if (threadPoolTask.isRepeating())
                        queue.add(threadPoolTask);
                }
            }
        }

        public void close() {
            closed = true;
            if(queue.isEmpty())
                queue.add(EMPTY_TASK);
        }
    }

    private static class DefaultThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r);
        }
    }

    private class Executor {
        private final Thread thread;
        private final ExecutionTask task;

        public Executor() {
            task = new ExecutionTask();
            thread = factory.newThread(task);
            thread.start();
        }

        public void close() {
            try {
                task.close();
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
