package com.lia.core.pipeline.ingest;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.lia.core.domain.law.Law;
import com.lia.core.observability.Obs;
import com.lia.core.pipeline.connector.LawConnector;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.diff.DiffBuilder;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.store.LawStore;

/**
 * 적재 오케스트레이터 — 오프라인 배치(D40). 기존 단계를 엮는다:
 * {@code LawConnector fetch → Normalizer → DiffBuilder → LawStore upsert}.
 *
 * <p>두 진입:
 * <ul>
 *   <li>{@link #store} — pending(+baseline) {@code RawLaw} → normalize→diff→upsert. <b>API 불필요</b>(테스트 가능).</li>
 *   <li>{@link #ingestPending} — 시행예정 목록을 훑어 각 법령을 fetch 후 {@code store}. <b>API 필요</b>.</li>
 * </ul>
 * 계측: {@code lia.ingest} span/timer. 설계: docs/status.md §4, [[LawStore]].
 */
@Component
public class IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final LawConnector connector;
    private final Normalizer normalizer;
    private final DiffBuilder diffBuilder;
    private final LawStore lawStore;
    private final ObservationRegistry observations;

    public IngestService(LawConnector connector, Normalizer normalizer, DiffBuilder diffBuilder,
                         LawStore lawStore, ObservationRegistry observations) {
        this.connector = connector;
        this.normalizer = normalizer;
        this.diffBuilder = diffBuilder;
        this.lawStore = lawStore;
        this.observations = observations == null ? ObservationRegistry.NOOP : observations;
    }

    /**
     * 조립 단계: pending {@code RawLaw}(+ 기준선 {@code RawLaw}, 제정이면 null) →
     * normalize → diff → 정본 upsert. 시행예정본과 (있으면) 시행중본을 모두 저장한다.
     */
    public IngestResult store(RawLaw pendingRaw, RawLaw baselineRaw) {
        return Observation.createNotStarted(Obs.INGEST, observations).observe(() -> {
            Law pending = normalizer.normalize(pendingRaw);
            Law baseline = baselineRaw == null ? null : normalizer.normalize(baselineRaw);
            Law diffed = diffBuilder.build(pending, baseline);

            lawStore.upsert(diffed);                    // 시행예정 정본(diff 포함)
            if (baseline != null) {
                lawStore.upsert(baseline);              // 시행중 정본(= diff 기준선)
            }
            return new IngestResult(diffed.lawId(), diffed.effectiveDate(),
                    diffed.changedArticles().size(), baseline != null);
        });
    }

    /**
     * 배치: 시행예정 목록(가장 이른 순) top-{@code limit} → 각 법령의 본문·기준선 fetch → {@code store}.
     * 제정 법령은 기준선이 없어({@code fetchCurrent}=null) 전부 신설로 저장된다.
     */
    public IngestSummary ingestPending(LocalDate from, LocalDate to, int limit) {
        List<RawLaw> heads = connector.listPending(from, to, limit);
        int stored = 0, withBaseline = 0;
        for (RawLaw head : heads) {
            RawLaw pending = connector.fetchPending(head.mst(), head.effectiveDate());
            RawLaw baseline = connector.fetchCurrent(head.lawId());   // null = 제정
            IngestResult r = store(pending, baseline);
            stored++;
            if (r.hasBaseline()) withBaseline++;
            log.info("[ingest] {} @{} — 변경 {}개, 기준선 {}", r.lawId(), r.effectiveDate(),
                    r.changedCount(), r.hasBaseline() ? "있음" : "없음(제정)");
        }
        return new IngestSummary(heads.size(), stored, withBaseline);
    }

    public record IngestResult(String lawId, LocalDate effectiveDate, int changedCount, boolean hasBaseline) {}

    public record IngestSummary(int listed, int stored, int withBaseline) {}
}
