package com.lia.core.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * 오프라인 회귀 평가 실행({@code eval} 프로파일, D83) — 정답 세트 → 평가 → 보고서 JSON 한 줄을 표준 출력에 →
 * 기준선이 있으면 비교. 한 번 돌고 끝난다. 유료 호출(Haiku·임베딩)이 있으므로 사람이 수동으로 돌린다.
 *
 * <p>종료 코드: {@link #PASSED} 통과(기준선 없음 포함) · {@link #FAILED} 실행 실패 · {@link #REGRESSED} 회귀.
 * 문항 하나라도 평가에 실패하면 보고서를 내지 않는다 — 조용히 빼면 분모가 바뀐다.
 * 스펙: docs/components/eval/OfflineEvaluator.md.
 */
public class OfflineEvalRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final int PASSED = 0;
    public static final int FAILED = 1;
    public static final int REGRESSED = 3;

    private static final Logger log = LoggerFactory.getLogger(OfflineEvalRunner.class);

    private final Function<String, CaseOutcome> evaluate;
    private final RagConfig config;
    private final LongSupplier indexedLaws;
    private final String goldPath;
    private final String baselinePath;
    private final RegressionGate gate;
    private final PrintStream out;
    private int exitCode = PASSED;

    /**
     * @param evaluate     질문 1건 → 결과({@link OfflineEvaluator#evaluate})
     * @param indexedLaws  색인된 법령 수
     * @param goldPath     정답 세트 위치 — 접두사 없으면 클래스패스, {@code file:} 이면 파일
     * @param baselinePath 기준선 위치(같은 규칙) — 없으면 비교하지 않는다
     * @param tolerance    회귀 허용 오차
     * @param out          보고서를 낼 곳(운영은 표준 출력)
     */
    public OfflineEvalRunner(Function<String, CaseOutcome> evaluate, RagConfig config, LongSupplier indexedLaws,
                             Predicate<String> pendingLaw, String goldPath, String baselinePath, double tolerance, PrintStream out) {
        this.evaluate = evaluate;
        this.config = config;
        this.indexedLaws = indexedLaws;
        this.goldPath = goldPath;
        this.baselinePath = baselinePath;
        this.gate = new RegressionGate(tolerance, OfflineMetrics.LOWER_IS_BETTER);
        this.out = out;
    }

    public int execute() {
        EvalReport report;
        try {
            List<EvalCase> cases = GoldenSet.cases(goldPath);
            List<CaseOutcome> outcomes = cases.stream().map(c -> evaluate.apply(c.query())).toList();
            report = new EvalReport(config, indexedLaws.getAsLong(), cases.size(), 0, OfflineMetrics.score(cases, outcomes));
        } catch (RuntimeException e) {
            log.error("[eval] 평가 실패 — 보고서를 내지 않는다", e);
            return FAILED;
        }
        out.println(report.toJson());

        Optional<EvalReport> baseline;
        try {
            baseline = readBaseline();
        } catch (RuntimeException e) {
            log.error("[eval] 기준선을 읽지 못했다: {}", baselinePath, e);
            return FAILED;
        }
        if (baseline.isEmpty()) {
            log.info("[eval] 기준선 없음({}) — 비교하지 않는다", baselinePath);
            return PASSED;
        }
        warnIfIncomparable(report, baseline.get());
        RegressionGate.Result result = gate.check(report, baseline.get());
        if (result.passed()) {
            log.info("[eval] 기준선 대비 회귀 없음");
            return PASSED;
        }
        result.failures().forEach(failure -> log.warn("[eval] {}", failure));
        return REGRESSED;
    }

    private Optional<EvalReport> readBaseline() {
        Resource resource = new DefaultResourceLoader().getResource(baselinePath);
        if (!resource.exists()) return Optional.empty();
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(EvalReport.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 설정·색인 규모가 다른 기준선과의 비교는 의미가 약하다 — 실패시키지 않고 사람에게 알린다. */
    private static void warnIfIncomparable(EvalReport report, EvalReport baseline) {
        if (!report.config().equals(baseline.config())) {
            log.warn("[eval] 법령 발견 설정이 기준선과 다르다: {} → {}", baseline.config(), report.config());
        }
        if (report.indexedLaws() != baseline.indexedLaws() || report.cases() != baseline.cases()) {
            log.warn("[eval] 색인 법령·문항 수가 기준선과 다르다: {}건·{}문항 → {}건·{}문항",
                    baseline.indexedLaws(), baseline.cases(), report.indexedLaws(), report.cases());
        }
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
