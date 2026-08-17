package com.lia.core.observability;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.diff.DiffBuilder;
import com.lia.core.pipeline.normalize.Normalizer;

/**
 * 데모 적재 러너 — <b>계기판 검증용 베이스라인 생성</b>(D48).
 *
 * <p>{@code --spring.profiles.active=demo} 로만 활성화. 번들 샘플 법령 fixture를
 * {@code normalize → diff} 로 한 번 돌려 <b>실측 타이머·span</b>을 만든다. 외부 API·온라인
 * 경로 불필요 — 순수 우리 오버헤드(§3.1)만 측정한다.
 *
 * <p>트레이스 트리: {@code lia.ingest} → {@code lia.normalize} · {@code lia.diff}.
 * 지표: {@code /actuator/prometheus} 의 {@code lia_normalize_*}·{@code lia_diff_*}·{@code lia_ingest_*}.
 */
@Component
@Profile("demo")
public class SampleIngestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleIngestRunner.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Normalizer normalizer;
    private final DiffBuilder diffBuilder;
    private final ObservationRegistry observations;

    public SampleIngestRunner(Normalizer normalizer, DiffBuilder diffBuilder,
                              ObservationRegistry observations) {
        this.normalizer = normalizer;
        this.diffBuilder = diffBuilder;
        this.observations = observations;
    }

    @Override
    public void run(String... args) {
        RawLaw raw = loadSample();

        Observation.createNotStarted(Obs.INGEST, observations).observe(() -> {
            Law pending = normalizer.normalize(raw);                    // → lia.normalize span
            Law diffed = diffBuilder.build(pending, syntheticBaseline()); // → lia.diff span

            log.info("[demo] 적재 계측 완료 — {} · 실조문 {}개 · 변경 {}개",
                    pending.title(), pending.realArticles().size(), pending.changedArticles().size());
            diffed.changedArticles().forEach(a -> log.info("[demo]   {} {} :: {}",
                    a.label(), a.changeType(), firstLine(a.diffVsCurrent())));
        });

        log.info("[demo] 지표 확인: GET /actuator/prometheus | grep -E 'lia_(normalize|diff|ingest)'");
    }

    /** 번들 샘플(시행예정본) → RawLaw. lawId·시행일 등은 Normalizer가 기본정보에서 채운다. */
    private RawLaw loadSample() {
        try (InputStream in = new ClassPathResource("samples/housing-act.json").getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = JSON.readValue(body, LinkedHashMap.class);
            return new RawLaw(null, null, null, "시행예정", null, null, null, root);
        } catch (IOException e) {
            throw new UncheckedIOException("데모 fixture 로드 실패", e);
        }
    }

    /** diff 기준선(시행중본) — 변경 조문(18·104·이동 57←56)에 대응하는 현행 조문만. */
    private Law syntheticBaseline() {
        LocalDate past = LocalDate.of(2020, 1, 1);
        List<Article> arts = List.of(
                baseArticle("18", "사업계획의 통합심의 등", "제18조(사업계획의 통합심의 등) 개별로 심의한다.", past),
                baseArticle("104", "벌칙", "제104조(벌칙) 1년 이하의 징역에 처한다.", past),
                baseArticle("56", "이동 전 조문", "제56조 통합심의 세부 절차는 다음과 같다.", past));
        return new Law("001809", "BASE", "주택법", Law.Status.시행중,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                past, "00000", past, null, null, null, null,
                List.of(), arts, List.of(), null, null, "base", Instant.now());
    }

    private static Article baseArticle(String no, String title, String text, LocalDate ef) {
        return new Article(no, title, text, false, Article.ChangeType.없음, null, null, ef, true, null);
    }

    private static String firstLine(String s) {
        return s == null ? "" : s.lines().findFirst().orElse("");
    }
}
