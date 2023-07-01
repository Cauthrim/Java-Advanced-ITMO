package info.kgeorgiy.ja.karpov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.AdvancedIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class IterativeParallelism implements AdvancedIP {
    private final ParallelMapper mapper;

    public IterativeParallelism() {
        this.mapper = null;
    }

    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }

    private <T, R> R generalMethod(int threads, List<? extends T> values, Function<Stream<? extends T>, R> mapping,
                                   Function<Stream<R>, R> reduction) throws InterruptedException {
        int threadCount = Math.min(threads, values.size());
        int threadSize = values.size() / threadCount;
        int remainder = values.size() % threadCount;
        List<Thread> threadList = new ArrayList<>();
        List<R> parallelResult = new ArrayList<>();
        List<Stream<? extends T>> valuesChunks = new ArrayList<>();

        int leftBorder = 0;
        for (int i = 0; i < threadCount; i++) {
            parallelResult.add(null);
            int rightBorder = leftBorder + threadSize;
            if (remainder > 0) {
                remainder--;
                rightBorder++;
            }
            valuesChunks.add(values.subList(leftBorder, rightBorder).stream());
            leftBorder = rightBorder;
        }

        if (mapper != null) {
            return reduction.apply(mapper.map(mapping, valuesChunks).stream());
        } else {
            for (int i = 0; i < threadCount; i++) {
                final int finalI = i;
                threadList.add(new Thread(() -> parallelResult.set(finalI, mapping.apply(valuesChunks.get(finalI)))));
                threadList.get(i).start();
            }

            InterruptedException exception = new InterruptedException("Thread run interrupted");
            for (int i = 0; i < threadCount; i++) {
                try {
                    threadList.get(i).join();
                } catch (InterruptedException e) {
                    exception.addSuppressed(e);
                }
            }
            if (exception.getSuppressed().length != 0) {
                throw exception;
            }
        }

        return reduction.apply(parallelResult.stream());
    }

    /**
     * Returns maximum value.
     *
     * @param threads    number of concurrent threads.
     * @param values     values to get maximum of.
     * @param comparator value comparator.
     * @param <T>        value type.
     * @return maximum of given values
     * @throws InterruptedException             if executing thread was interrupted.
     * @throws java.util.NoSuchElementException if no values are given.
     */
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator)
            throws InterruptedException {
        if (values.isEmpty()) {
            throw new NoSuchElementException("No values are given");
        }

        return generalMethod(threads, values, 
                stream -> stream.max(comparator).orElse(null),
                stream -> stream.max(comparator).orElse(null));
    }

    /**
     * Returns minimum value.
     *
     * @param threads    number of concurrent threads.
     * @param values     values to get minimum of.
     * @param comparator value comparator.
     * @param <T>        value type.
     * @return minimum of given values
     * @throws InterruptedException             if executing thread was interrupted.
     * @throws java.util.NoSuchElementException if no values are given.
     */
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator)
            throws InterruptedException {
        return maximum(threads, values, comparator.reversed());
    }

    /**
     * Returns whether all values satisfy predicate.
     *
     * @param threads   number of concurrent threads.
     * @param values    values to test.
     * @param predicate test predicate.
     * @param <T>       value type.
     * @return whether all values satisfy predicate or {@code true}, if no values are given.
     * @throws InterruptedException if executing thread was interrupted.
     */
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return generalMethod(threads, values, stream -> stream.allMatch(predicate),
                booleanStream -> booleanStream.allMatch(value -> value));
    }

    /**
     * Returns whether any of values satisfies predicate.
     *
     * @param threads   number of concurrent threads.
     * @param values    values to test.
     * @param predicate test predicate.
     * @param <T>       value type.
     * @return whether any value satisfies predicate or {@code false}, if no values are given.
     * @throws InterruptedException if executing thread was interrupted.
     */
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return !all(threads, values, Predicate.not(predicate));
    }

    /**
     * Returns number of values satisfying predicate.
     *
     * @param threads   number of concurrent threads.
     * @param values    values to test.
     * @param predicate test predicate.
     * @param <T>       value type.
     * @return number of values satisfying predicate.
     * @throws InterruptedException if executing thread was interrupted.
     */
    public <T> int count(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return generalMethod(threads, values,
                stream -> stream.filter(predicate).toList().size(),
                stream -> stream.reduce(Integer::sum).orElse(0));
    }

    /**
     * Join values to string.
     *
     * @param threads number of concurrent threads.
     * @param values  values to join.
     * @return list of joined results of {@link #toString()} call on each value.
     * @throws InterruptedException if executing thread was interrupted.
     */
    public String join(int threads, List<?> values) throws InterruptedException {
        return generalMethod(threads, values,
                stream -> stream.map(String::valueOf).reduce((l, r) -> l + r).orElse(null),
                stream -> stream.reduce((l, r) -> l + r).orElse(null));
    }

    /**
     * Filters values by predicate.
     *
     * @param threads   number of concurrent threads.
     * @param values    values to filter.
     * @param predicate filter predicate.
     * @return list of values satisfying given predicate. Order of values is preserved.
     * @throws InterruptedException if executing thread was interrupted.
     */
    public <T> List<T> filter(final int threads, final List<? extends T> values, final Predicate<? super T> predicate)
            throws InterruptedException {
        return generalMethod(threads, values,
                stream -> stream.map(obj -> (T) obj).filter(predicate).toList(),
                listStream -> listStream.flatMap(Collection::stream).toList());
    }

    /**
     * Maps values.
     *
     * @param threads number of concurrent threads.
     * @param values  values to map.
     * @param f       mapper function.
     * @return list of values mapped by given function.
     * @throws InterruptedException if executing thread was interrupted.
     */
    public <T, U> List<U> map(final int threads, final List<? extends T> values,
                              final Function<? super T, ? extends U> f) throws InterruptedException {
        return generalMethod(threads, values,
                stream -> stream.map(f).map(obj -> (U) obj).toList(),
                listStream -> listStream.flatMap(Collection::stream).toList());
    }

    private <T, R> R generalReduce(final int threads, List<T> values, final Function<T, R> mapping,
                                   Monoid<R> monoid) throws InterruptedException {
        return generalMethod(threads, values,
                stream -> stream.map(mapping).reduce(monoid.getOperator()).orElse(monoid.getIdentity()),
                stream -> stream.reduce(monoid.getOperator()).orElse(monoid.getIdentity()));
    }

    /**
     * Reduces values using monoid.
     *
     * @param threads number of concurrent threads.
     * @param values values to reduce.
     * @param monoid monoid to use.
     *
     * @return values reduced by provided monoid or {@link Monoid#getIdentity() identity} if no values specified.
     *
     * @throws InterruptedException if executing thread was interrupted.
     */
    public <T> T reduce(final int threads, List<T> values, final Monoid<T> monoid) throws InterruptedException {
        return generalReduce(threads, values, Function.identity(), monoid);
    }

    /**
     * Maps and reduces values using monoid.
     *
     * @param threads number of concurrent threads.
     * @param values values to reduce.
     * @param lift mapping function.
     * @param monoid monoid to use.
     *
     * @return values reduced by provided monoid or {@link Monoid#getIdentity() identity} if no values specified.
     *
     * @throws InterruptedException if executing thread was interrupted.
     */
    public <T, R> R mapReduce(final int threads, final List<T> values, final Function<T, R> lift,
                              final Monoid<R> monoid) throws InterruptedException {
        return generalReduce(threads, values, lift, monoid);
    }
}
