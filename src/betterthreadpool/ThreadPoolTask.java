package betterthreadpool;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * {@code betterthreadpool.ThreadPoolTask} is a class used to contain {@code Runnable} code for scheduled execution in a {@link ThreadPool}.
 */
public class ThreadPoolTask {
    private final long interval;
    private final long delay;
    private long originTime;
    private final Runnable task;
    private boolean isCancelled;
    private final boolean isRepeating;
    private boolean isExecuting;

    protected ThreadPoolTask(Runnable task, long interval, long delay, TimeUnit unit) {
        this.interval = unit.toNanos(interval);
        this.delay = unit.toNanos(delay);
        originTime = System.nanoTime();
        this.task = task;
        isRepeating = true;
        isExecuting = false;
    }

    protected ThreadPoolTask(Runnable task, long delay, TimeUnit unit) {
        this.interval = -1;
        this.delay = unit.toNanos(delay);
        originTime = System.nanoTime();
        this.task = task;
        isRepeating = false;
        isExecuting = false;
    }

    protected ThreadPoolTask() {
        this.interval = -1;
        this.delay = -1;
        originTime = -1;
        this.task = null;
        isRepeating = false;
        isExecuting = false;
    }

    protected synchronized void execute() {
        if(isExecutable()) {
            isExecuting = true;
            task.run();
            originTime = System.nanoTime();
            isExecuting = false;
        } else {
            if(task != null) {
                if (!isRepeating)
                    reject("Attempted to repeat nonrepeating ThreadPoolTask");
                else if (isCancelled)
                    reject("Attempted to execute cancelled ThreadPoolTask");
                else
                    reject("Failure to execute ThreadPoolTask");
            }
        }
    }

    /**
     * Returns if the task is currently able to execute.
     * @return true if the task can be executed
     */
    public boolean isExecutable() {
        return task != null && System.nanoTime()-originTime-delay > interval && !isCancelled && !isExecuting;
    }

    /**
     * Returns whether the task is repeating on an interval.
     * @return true if the task is scheduled to repeat
     */
    public boolean isRepeating() {
        return isRepeating;
    }

    /**
     * Returns the nanosecond interval at which the timer is executing.
     * @return The nanosecond interval between executions, -1 if non-repeating
     */
    public long getInterval() {
        return interval;
    }

    /**
     * Returns the nanosecond delay before initial task execution.
     * @return The nanosecond delay before first execution
     */
    public long getDelay() {
        return delay;
    }

    /**
     * Returns whether the task is cancelled or not, whether from executing once and being non-repeating or being manually cancelled through {@code cancel()}.
     * @return true if the task has been cancelled
     */
    public boolean isCancelled() {
        return isCancelled;
    }

    /**
     * Cancels the task, preventing it from ever executing again.
     */
    public void cancel() {
        isCancelled = true;
    }

    private void reject(String reason) {
        throw new RejectedExecutionException(reason);
    }
}
