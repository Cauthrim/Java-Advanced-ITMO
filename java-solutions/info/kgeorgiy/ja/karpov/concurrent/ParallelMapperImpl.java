package info.kgeorgiy.ja.karpov.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {
    private final Queue<Runnable> tasks;
    private final List<Thread> threads;

    private static class mapResults<T> {
        List<T> list;
        int cnt;

        public mapResults(int size) {
            list = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                list.add(null);
            }
        }
    }

    /**
     * Default constructor used to create an instance with one thread.
     */
    public ParallelMapperImpl() {
        this(1);
    }

    /**
     * Constructor used to create worker threads by specified number.
     *
     * @param threads - the number of threads to create.
     */
    public ParallelMapperImpl(int threads) {
        tasks = new ArrayDeque<>();
        this.threads = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            this.threads.add(new Thread(() -> {
                Runnable current;
                while(true) {
                    synchronized (tasks) {
                        try {
                            while (tasks.isEmpty()) {
                                tasks.wait();
                            }
                            current = tasks.remove();
                            tasks.notify();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    current.run();
                }
            }));
            this.threads.get(i).start();
        }
    }

    /**
     * Maps function {@code f} over specified {@code args}.
     * Mapping for each element performed in parallel.
     *
     * @throws InterruptedException if calling thread was interrupted
     */
    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args) throws InterruptedException {
        mapResults<R> results = new mapResults<>(args.size());

        synchronized (tasks) {
            for (int i = 0; i < args.size(); i++) {
                final int finalI = i;
                tasks.add(
                        () -> {
                            R res = f.apply(args.get(finalI));
                            synchronized (results) {
                                results.list.set(finalI, res);
                                results.cnt++;
                                if (results.cnt == args.size()) {
                                    results.notify();
                                }
                            }
                        }
                );
            }
            tasks.notify();
        }

        synchronized (results) {
            while (results.cnt != args.size()) {
                results.wait();
            }
            return results.list;
        }
    }

    /** Stops all threads. All unfinished mappings are left in undefined state. */
    @Override
    public void close() {
        for (Thread thread : threads) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException ignored) {}
        }
    }
}