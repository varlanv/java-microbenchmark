package com.varlanv.bench;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Bench {

    public static Bench bench() {
        return new Bench();
    }

    public BenchWithOneSubject addSubject(ThrowingFunction<AddField, AddEnd> specAction) {
        return new BenchWithOneSubject(
                Objects.requireNonNull(
                        specAction.toFunction().apply(new AddField()).spec
                )
        );
    }

    public static final class ExtendedSpec {

        private ThrowingRunnable cleanup = () -> {
        };
    }

    public static final class BenchWithOneSubject {

        private final Spec subject;

        public BenchWithOneSubject(Spec subject) {
            this.subject = subject;
        }

        public BenchWithManySubjects addSubject(ThrowingFunction<AddField, AddEnd> specAction) {
            Spec newSubject = Objects.requireNonNull(
                    specAction.toFunction().apply(new AddField()).spec
            );
            LinkedHashMap<String, Spec> subjects = new LinkedHashMap<>();
            subjects.put(subject.name(), subject);
            subjects.put(newSubject.name(), newSubject);
            return new BenchWithManySubjects(subjects);
        }

        public Result run() {
            return new BenchWithManySubjects(subject)
                    .run()
                    .values()
                    .iterator()
                    .next();
        }

        public void runAndPrintResult() {
            new BenchWithManySubjects(subject)
                    .runAndPrintResult();
        }
    }

    public static final class BenchWithManySubjects {

        private final Map<String, Spec> subjects;

        BenchWithManySubjects(Map<String, Spec> subjects) {
            this.subjects = subjects;
        }

        BenchWithManySubjects(Spec subject) {
            this.subjects = new LinkedHashMap<>();
            this.subjects.put(subject.name(), subject);
        }

        public BenchWithManySubjects() {
            this(new LinkedHashMap<>());
        }

        public BenchWithManySubjects addSubject(ThrowingFunction<AddField, Spec> specAction) {
            Spec spec = specAction.toFunction().apply(new AddField());
            if (subjects.containsKey(spec.name())) {
                throw new IllegalArgumentException("Subject already exists: " + spec.name());
            }
            LinkedHashMap<String, Spec> newSubjects = new LinkedHashMap<>(subjects);
            newSubjects.put(spec.name(), spec);
            return new BenchWithManySubjects(newSubjects);
        }

        public Map<String, Result> run() {
            int iterations = 1_000_000;

            // Measure time for the loop with nanoTime() calls
            long startTimeWithNano = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                System.nanoTime();
            }
            long endTimeWithNano = System.nanoTime();
            long totalTimeWithNano = endTimeWithNano - startTimeWithNano;

            // Measure time for an empty loop
            long startTimeEmptyLoop = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                // Empty loop
            }
            long endTimeEmptyLoop = System.nanoTime();
            long totalTimeEmptyLoop = endTimeEmptyLoop - startTimeEmptyLoop;

            // Estimate the overhead of System.nanoTime() calls
            long nanoTimeOnly = totalTimeWithNano - totalTimeEmptyLoop;
            double averageNanoTime = (double) nanoTimeOnly / iterations;

            Collection<Spec> specs = subjects.values();
            Map<String, Result> results = new LinkedHashMap<>();
            try {
                for (Spec spec : specs) {
                    ThrowingRunnable action = spec.action();
                    ThrowingRunnable cleanup = spec.extended().cleanup;
                    for (long warmup = 0; warmup < spec.warmupCycles(); warmup++) {
                        action.run();
                        cleanup.run();
                    }
                }
                for (Spec spec : specs) {
                    double average = 0.0;
                    long min = 0;
                    long max = 0;
                    ThrowingRunnable action = spec.action();
                    ThrowingRunnable cleanup = spec.extended().cleanup;
                    for (long iteration = 0; iteration < spec.iterationCycles(); iteration++) {
                        long nanoBefore = System.nanoTime();
                        action.run();
                        long nanoAfter = System.nanoTime();
                        average += (nanoAfter - nanoBefore);
                        min = Math.min(min, nanoAfter - nanoBefore);
                        max = Math.max(max, nanoAfter - nanoBefore);
                        cleanup.run();
                    }
                    results.put(
                            spec.name(),
                            new Result(
                                    BigDecimal.valueOf(average / spec.iterationCycles() - averageNanoTime).setScale(2, RoundingMode.HALF_UP),
                                    min,
                                    max
                            ));
                }
            } catch (Throwable e) {
                throw hide(e);
            }
            return Collections.unmodifiableMap(results);
        }

        public void runAndPrintResult() {
            run().forEach((name, result) -> System.out.println(name + ": " + result));
        }
    }

    public interface Spec {

        String name();

        long warmupCycles();

        long iterationCycles();

        ThrowingRunnable action();

        ExtendedSpec extended();
    }

    static final class SpecImpl implements Spec {

        private final String name;
        private final long warmupCycles;
        private final long iterationCycles;
        private final ThrowingRunnable action;

        public SpecImpl(String name,
                        long warmupCycles,
                        long iterationCycles,
                        ThrowingRunnable action) {
            this.name = name;
            this.warmupCycles = warmupCycles;
            this.iterationCycles = iterationCycles;
            this.action = action;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public long warmupCycles() {
            return warmupCycles;
        }

        @Override
        public long iterationCycles() {
            return iterationCycles;
        }

        @Override
        public ThrowingRunnable action() {
            return action;
        }

        @Override
        public ExtendedSpec extended() {
            return new ExtendedSpec();
        }
    }

    public static final class AddField {

        public AddWarmup named(CharSequence fieldName) {
            if (fieldName.length() == 0) {
                throw new IllegalArgumentException("fieldName must not be empty");
            }
            return new AddWarmup(fieldName.toString());
        }
    }

    public static final class AddEnd {

        private final SpecImpl spec;

        AddEnd(SpecImpl spec) {
            this.spec = spec;
        }

        public AddEnd withCleanupFunction(ThrowingRunnable cleanupFunction) {
            spec.extended().cleanup = cleanupFunction;
            return this;
        }
    }

    public static final class AddWarmup {

        private final String name;

        public AddWarmup(String name) {
            this.name = name;
        }

        public AddCycles withWarmupCycles(long warmup) {
            if (warmup < 0) {
                throw new IllegalArgumentException("warmup cannot be negative");
            }
            return new AddCycles(warmup, this);
        }
    }

    public static final class AddCycles {

        private final long warmup;
        private final AddWarmup parent;

        public AddCycles(long warmup, AddWarmup parent) {
            this.warmup = warmup;
            this.parent = parent;
        }

        public AddIterations withIterations(long iterations) {
            if (iterations < 0) {
                throw new IllegalArgumentException("iterations cannot be negative");
            }
            return new AddIterations(iterations, this);
        }
    }

    public static final class AddIterations {

        private final long iterations;
        private final AddCycles parent;

        public AddIterations(long iterations, AddCycles parent) {
            this.iterations = iterations;
            this.parent = parent;
        }

        public AddEnd withAction(ThrowingRunnable action) {
            return new AddEnd(
                    new SpecImpl(
                            parent.parent.name,
                            parent.warmup,
                            iterations,
                            Objects.requireNonNull(action)
                    )
            );
        }
    }

    public static final class Result {

        private final BigDecimal average;
        private final long min;
        private final long max;

        public Result(BigDecimal average, long min, long max) {
            this.average = average;
            this.min = min;
            this.max = max;
        }

        public BigDecimal average() {
            return average;
        }

        public long min() {
            return min;
        }

        public long max() {
            return max;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "average=" + average +
                    ", min=" + min +
                    ", max=" + max +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Result)) {
                return false;
            }
            Result result = (Result) o;
            return min == result.min && max == result.max && Objects.equals(average, result.average);
        }

        @Override
        public int hashCode() {
            return Objects.hash(average, min, max);
        }
    }

    public interface ThrowingRunnable {

        void run() throws Throwable;

        default Runnable toRunnable() {
            return () -> {
                try {
                    run();
                } catch (Throwable e) {
                    throw hide(e);
                }
            };
        }
    }

    public interface ThrowingConsumer<T> {

        void accept(T t) throws Throwable;

        default Consumer<T> toConsumer() {
            return t -> {
                try {
                    accept(t);
                } catch (Throwable e) {
                    throw hide(e);
                }
            };
        }
    }

    public interface ThrowingFunction<T, R> {

        R apply(T t) throws Throwable;

        default Function<T, R> toFunction() {
            return t -> {
                try {
                    return apply(t);
                } catch (Throwable e) {
                    throw hide(e);
                }
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> T hide(Throwable t) throws T {
        throw (T) t;
    }
}
