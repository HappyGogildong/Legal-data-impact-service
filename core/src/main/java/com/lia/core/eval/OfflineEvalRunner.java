package com.lia.core.eval;

import java.io.PrintStream;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;

/** 오프라인 평가 실행(D83) — RED 단계 껍데기. */
public class OfflineEvalRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final int PASSED = 0;
    public static final int FAILED = 1;
    public static final int REGRESSED = 3;

    private int exitCode = PASSED;

    public OfflineEvalRunner(Function<String, CaseOutcome> evaluate, RagConfig config, LongSupplier indexedLaws,
                             String goldPath, String baselinePath, double tolerance, PrintStream out) {}

    public int execute() {
        return -1;
    }

    @Override
    public void run(ApplicationArguments args) {
        exitCode = execute();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
