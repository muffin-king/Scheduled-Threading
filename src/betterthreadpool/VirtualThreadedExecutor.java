package betterthreadpool;

import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.*;

public class VirtualThreadedExecutor extends AbstractExecutor {
    private Executor[] executors;
    private final Deque<ExecutorTask> queue;
    private boolean isClosed;
    private static final ThreadFactory FACTORY = Thread.ofVirtual().factory();
    private final long TIMEOUT;

    public VirtualThreadedExecutor(long timeout) {
        super(FACTORY);
        isClosed = false;
        executors = new Executor[0];
        queue = new ConcurrentLinkedDeque<>();
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
