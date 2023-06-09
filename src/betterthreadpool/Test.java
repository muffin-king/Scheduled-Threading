package betterthreadpool;

import java.util.concurrent.TimeUnit;

public class Test {
    public static void main(String[] args) throws InterruptedException {
        CachedThreadedExecutor pool = new CachedThreadedExecutor(1000);
        pool.execute(new TestTask1());
        pool.execute(new TestTask2());
        pool.execute(new TestTask1());

        Thread.sleep(10000);
        System.out.println("cookie");
    }

    private static class TestTask1 implements Runnable {
        public void run() {
            System.out.println("!");
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class TestTask2 implements Runnable {
        public void run() {
            System.out.println("?");
        }
    }
}
