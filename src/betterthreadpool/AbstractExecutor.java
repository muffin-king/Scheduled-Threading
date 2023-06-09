package betterthreadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class AbstractExecutor implements Executor {
    protected ThreadFactory factory;

    protected AbstractExecutor(ThreadFactory factory) {

    }

    @Override
    public ExecutorTask scheduleTask(Runnable task, long interval, long delay, TimeUnit unit) {
        return null;
    }

    @Override
    public ExecutorTask scheduleTask(Runnable task, long delay, TimeUnit unit) {
        return null;
    }

    @Override
    public ExecutorTask submit(Runnable task) {
        return null;
    }

    @Override
    public ExecutorTask execute(Runnable task) {
        return null;
    }

    @Override
    public int getThreadCount() {
        return 0;
    }

    @Override
    public void close() {

    }
}
