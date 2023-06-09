package betterthreadpool;

import java.util.concurrent.TimeUnit;

public interface Executor {
    /**
     * Schedules a new {@code Runnable} for execution at an interval.
     * @param task The {@code Runnable} to execute
     * @param interval The interval between execution
     * @param delay The delay before initial execution
     * @param unit The {@code TimeUnit} of the interval and delay
     * @return The {@code ExecutorTask} created
     */
    ExecutorTask scheduleTask(Runnable task, long interval, long delay, TimeUnit unit);

    /**
     * Schedules a new {@code Runnable} for execution.
     * @param task The {@code Runnable} to execute
     * @param delay The delay before initial execution
     * @param unit The {@code TimeUnit} of the interval and delay
     * @return The {@code ExecutorTask} created
     */
    ExecutorTask scheduleTask(Runnable task, long delay, TimeUnit unit);

    /**
     * Submits a task to be executed as soon as possible.
     * @param task The {@code Runnable} to execute
     * @return The {@code ExecutorTask} created
     */
    ExecutorTask submit(Runnable task);

    /**
     * Submits a task to be executed immediately, placing it at the front of the task queue.
     * @param task The {@code Runnable} to execute
     * @return The {@code ExecutorTask} created
     */
    ExecutorTask execute(Runnable task);

    /**
     * Gets the current number of threads in the pool.
     * @return The number of threads
     */
    int getThreadCount();

    /**
     * Closes the thread pool, blocking until all currently executing tasks are complete and all threads can be safely closed and discarded.
     * Cannot be reopened once closed.
     */
    void close();
}
