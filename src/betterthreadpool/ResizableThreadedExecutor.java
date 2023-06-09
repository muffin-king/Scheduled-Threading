package betterthreadpool;

import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.*;

/**
 * A class used for instantiating a number of {@link Thread Threads} that
 * can be used to execute {@link Runnable} tasks, immediately, at a delay, or repeatedly.
 *
 * A {@code ResizableThreadedExecutor} works by using a {@link Deque} to store {@link ExecutorTask}s for execution. Each thread in the pool attempts
 * to grab a task from the head of the queue every loop. If there is an available task that has met the requirements to be executed (the task's delay
 * and the time before a repeated execution has also elapsed, and the task is not cancelled), then the task is executed by the first thread to
 * acquire it, without guarantees as to which thread acquires it. If the task at the head of the queue does not meet the requirements for execution,
 * then the task is placed back into the back of the queue.
 */
public class ResizableThreadedExecutor implements Executor {
    private Executor[] executors;
    private final Deque<ExecutorTask> queue;
    private boolean isClosed;
    private ThreadFactory factory;
    private static final ThreadFactory DEFAULT_FACTORY = new DefaultThreadFactory();
    private static final ExecutorTask EMPTY_TASK = new ExecutorTask();

    /**
     * Constructs a new {@code ScheduledThreadPool}.
     * @param threadCount The number of threadCount to instantiate the pool with
     */
    public ResizableThreadedExecutor(int threadCount) {
        isClosed = false;
        executors = new Executor[threadCount];
        queue = new ConcurrentLinkedDeque<>();
        factory = DEFAULT_FACTORY;
        populateThreads();
    }

    /**
     * Constructs a new {@code ScheduledThreadPool}.
     * @param threadCount The number of threadCount to instantiate the pool with
     * @param factory The {@code ThreadFactory} to use when instantiating threads
     */
    public ResizableThreadedExecutor(int threadCount, ThreadFactory factory) {
        if(factory == null)
            throw new NullPointerException();
        isClosed = false;
        executors = new Executor[threadCount];
        queue = new ConcurrentLinkedDeque<>();
        this.factory = factory;
        populateThreads();
    }

    @Override
    public ExecutorTask scheduleTask(Runnable task, long interval, long delay, TimeUnit unit) {
        if(task == null || unit == null)
            throw new NullPointerException();
        ExecutorTask executorTask = new ExecutorTask(task, interval, delay, unit);
        queue.add(executorTask);
        return executorTask;
    }

    @Override
    public ExecutorTask scheduleTask(Runnable task, long delay, TimeUnit unit) {
        if(task == null || unit == null)
            throw new NullPointerException();
        ExecutorTask executorTask = new ExecutorTask(task, delay, unit);
        queue.add(executorTask);
        return executorTask;
    }

    @Override
    public ExecutorTask submit(Runnable task) {
        if(task == null)
            throw new NullPointerException();
        ExecutorTask executorTask = new ExecutorTask(task, 0, TimeUnit.NANOSECONDS);
        queue.add(executorTask);
        return executorTask;
    }

    @Override
    public ExecutorTask execute(Runnable task) {
        if(task == null)
            throw new NullPointerException();
        ExecutorTask executorTask = new ExecutorTask(task, 0, TimeUnit.NANOSECONDS);
        queue.addFirst(executorTask);
        return executorTask;
    }

    private void populateThreads() {
        for(int i = 0; i < executors.length; i++) {
            if(executors[i] == null) {
                executors[i] = new Executor();
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
            executors = Arrays.copyOf(executors, threadCount);
            populateThreads();
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getThreadCount() {
        return executors.length;
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

    @Override
    public void close() {
        try {
            removeExcessThreads(-executors.length);
            isClosed = true;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        queue.clear();
    }

    private void removeExcessThreads(int threadCount) throws InterruptedException {
        for(int i = 0; i < executors.length; i++) {
            if(i > threadCount) {
                executors[i].close();
                executors[i] = null;
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
                ExecutorTask executorTask = queue.poll();
                if (executorTask != null) {
                    if (executorTask.isExecutable())
                        executorTask.execute();
                    if (executorTask.isRepeating())
                        queue.add(executorTask);
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
