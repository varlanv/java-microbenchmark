import impl.Bench;

import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {
        Bench.bench().addSubject(spec -> spec
                        .named("1")
                        .withWarmupCycles(100_000)
                        .withIterations(1_000_000)
                        .withAction(() -> {
                            try {
                                throw new RuntimeException("kek");
                            } catch (Throwable e) {
                            }
                        }))
                .addSubject(spec -> spec
                        .named("2")
                        .withWarmupCycles(100_000)
                        .withIterations(1_000_000)
                        .withAction(() -> {new ArrayList<>().add(1);}))
                .runAndPrintResult();
    }

}
