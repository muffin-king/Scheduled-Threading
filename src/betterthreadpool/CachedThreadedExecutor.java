package betterthreadpool;

import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.*;

public class CachedThreadedExecutor implements Executor {
    private Executor[] executors;
    private final Deque<ExecutorTask> queue;
    private boolean isClosed;
    private ThreadFactory factory;
    private static final ThreadFactory DEFAULT_FACTORY = new DefaultThreadFactory();
    private final long TIMEOUT;

    public CachedThreadedExecutor(long timeout, ThreadFactory factory) {
        if(factory == null)
            throw new NullPointerException();
        isClosed = false;
        executors = new Executor[0];
        queue = new ConcurrentLinkedDeque<>();
        this.factory = factory;
        this.TIMEOUT = timeout * 1000000;
    }

    public CachedThreadedExecutor(long timeout) {
        isClosed = false;
        executors = new Executor[0];
        queue = new ConcurrentLinkedDeque<>();
        this.factory = DEFAULT_FACTORY;
        this.TIMEOUT = timeout * 1000000;
    }

    @Override
    public ExecutorTask scheduleTask(Runnable task, long interval, long delay, TimeUnit unit) {
        ExecutorTask executorTask = new ExecutorTask(task, interval, delay, TimeUnit.NANOSECONDS);
        addExecutorTask(executorTask);
        return executorTask;
    }

    @Override
    public ExecutorTask scheduleTask(Runnable task, long delay, TimeUnit unit) {
        ExecutorTask executorTask = new ExecutorTask(task, delay, TimeUnit.NANOSECONDS);
        addExecutorTask(executorTask);
        return executorTask;
    }

    @Override
    public ExecutorTask submit(Runnable task) {
        ExecutorTask executorTask = new ExecutorTask(task, 0, TimeUnit.NANOSECONDS);
        addExecutorTask(executorTask);
        return executorTask;
    }

    @Override
    public ExecutorTask execute(Runnable task) {
        ExecutorTask executorTask = new ExecutorTask(task, 0, TimeUnit.NANOSECONDS);
        if(getAvailableThreadCount() == 0)
            setThreadArraySize(executors.length + 1);
        queue.addFirst(executorTask);
        return executorTask;
    }

    private void addExecutorTask(ExecutorTask executorTask) {
        if(getAvailableThreadCount() == 0)
            setThreadArraySize(executors.length + 1);
        queue.add(executorTask);
    }

    private int getAvailableThreadCount() {
        int threads = 0;
        for(Executor executor : this.executors)
            if(executor != null && !executor.isExecuting())
                threads++;
        return threads;
    }

    private void populateThreads(int count) {
        int populated = 0;
        for(int i = 0; i < executors.length; i++) {
            if(executors[i] == null) {
                executors[i] = new Executor();
                populated++;
            }
            if(populated == count)
                break;
        }
    }

    private void setThreadArraySize(int threadCount) {
        try {
            removeExcessThreads(threadCount);
            executors = Arrays.copyOf(executors, threadCount);
            populateThreads(1);
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getThreadCount() {
        return executors.length;
    }

    public ThreadFactory getFactory() {
        return factory;
    }

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

    private void nullifyThread(ExecutionTask executionTask) {
        for(int i = 0; i < executors.length; i++) {
            if (executors[i] != null && executors[i].task == executionTask) {
                executionTask.close();
                executors[i] = null;
                return;
            }
        }
    }

    private class ExecutionTask implements Runnable {
        private boolean closed;
        private boolean isExecuting;
        private final long START_TIME;
        public ExecutionTask() {
            closed = false;
            isExecuting = false;
            START_TIME = System.nanoTime();
        }

        @Override
        public void run() {
            while(!closed) {
                if(System.nanoTime()-START_TIME >= TIMEOUT) {
                    nullifyThread(this);
                }

                ExecutorTask executorTask = queue.poll();

                if(executorTask != null) {
                    isExecuting = true;

                    if (executorTask.isExecutable())
                        executorTask.execute();
                    if (executorTask.isRepeating())
                        throw new RuntimeException("Repeating task in CachedThreadedExecutor queue");

                    isExecuting = false;
                }
            }
        }

        public void close() {
            closed = true;
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

        public boolean isExecuting() {
            return task.isExecuting;
        }
    }
}
