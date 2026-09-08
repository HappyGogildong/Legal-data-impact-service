package com.lia.core.dev;

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

import tools.jackson.databind.ObjectMapper;

import com.lia.core.domain.law.Article;
import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.ingest.IngestService;
import com.lia.core.store.LawStore;

/**
 * 데모 시드 러너 — {@code --spring.profiles.active=seed} 로만 활성화(운영 무영향).
 * 번들 샘플 주택법(001809@2026-08-04, 일부개정)을 {@code law_versions} 에 적재해
 * {@code POST /api/v1/analyses} 가 실제 그라운딩 answer 를 내도록 만든다(온라인 관통 데모).
 *
 * <p>적재는 <b>임베딩 프리</b>(store 경로) — OpenAI 호출 없음. DIFF 데모를 위해 시행중 기준선도 함께.
 * 실 배치 적재 트리거(IngestService.ingestPending)는 별개(오프라인 스케줄러, 후속).
 */
@Component
@Profile("seed")
public class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final IngestService ingest;
    private final LawStore lawStore;

    public SeedRunner(IngestService ingest, LawStore lawStore) {
        this.ingest = ingest;
        this.lawStore = lawStore;
    }

    @Override
    public void run(String... args) {
        lawStore.upsert(syntheticBaseline());                  // 시행중 기준선(DIFF 대조용)
        IngestService.IngestResult r = ingest.store(loadSample(), null);  // 시행예정 정본(임베딩 프리)
        log.info("[seed] 적재 완료 — {}@{} 변경 {}건 + 시행중 기준선", r.lawId(), r.effectiveDate(), r.changedCount());
    }

    /** 번들 샘플(시행예정본) → RawLaw. lawId·시행일은 Normalizer 가 기본정보에서 채운다. */
    private RawLaw loadSample() {
        try (InputStream in = new ClassPathResource("samples/housing-act.json").getInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = JSON.readValue(body, LinkedHashMap.class);
            return new RawLaw(null, null, null, "시행예정", null, null, null, root);
        } catch (IOException e) {
            throw new UncheckedIOException("시드 fixture 로드 실패", e);
        }
    }

    /** diff 기준선(시행중본) — 변경 조문(18·104·이동 57←56) 대응 현행 조문(SampleIngestRunner와 동일). */
    private Law syntheticBaseline() {
        LocalDate past = LocalDate.of(2020, 1, 1);
        List<Article> arts = List.of(
                base("18", "사업계획의 통합심의 등", "제18조(사업계획의 통합심의 등) 개별로 심의한다.", past),
                base("104", "벌칙", "제104조(벌칙) 1년 이하의 징역에 처한다.", past),
                base("56", "이동 전 조문", "제56조 통합심의 세부 절차는 다음과 같다.", past));
        return new Law("001809", "BASE", "주택법", Law.Status.시행중,
                Law.AmendKind.일부개정, Law.LawType.법률, "국토교통부",
                past, "00000", past, null, null, null, null,
                List.of(), arts, List.of(), null, null, "base", Instant.now());
    }

    private static Article base(String no, String title, String text, LocalDate ef) {
        return new Article(no, title, text, false, Article.ChangeType.없음, null, null, ef, true, null);
    }
}
