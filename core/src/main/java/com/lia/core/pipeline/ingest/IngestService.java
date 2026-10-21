package com.lia.core.pipeline.ingest;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import com.lia.core.domain.law.Law;
import com.lia.core.observability.Obs;
import com.lia.core.pipeline.connector.RawLaw;
import com.lia.core.pipeline.diff.DiffBuilder;
import com.lia.core.pipeline.normalize.Normalizer;
import com.lia.core.store.LawStore;

/**
 * 법령 한 건의 정본 조립 — {@code Normalizer → DiffBuilder → LawStore upsert}.
 *
 * <p>배치 실행·fetch·재시도는 IngestJob(D62)이 맡고, 이 클래스는 그 {@code SYNC} 작업이 부르는 조립 단위다.
 * 외부 API 를 부르지 않고 트랜잭션도 열지 않는다(호출자 트랜잭션에 참여 — SyncHandler 가 INDEX 등록과 묶는다).
 * <b>revision·상태가 같으면 쓰지 않는다</b>(증분, D16). 시행예정본의 저장 revision 은
 * {@code <본문 revision>+<기준선 revision 또는 none>} — diff 가 기준선에도 의존하므로 기준선만 바뀌어도
 * (시행 전환 등) 다시 저장된다. 설계: docs/components/ingest/IngestService.md
 */
@Component
public class IngestService {

    private final Normalizer normalizer;
    private final DiffBuilder diffBuilder;
    private final LawStore lawStore;
    private final ObservationRegistry observations;

    public IngestService(Normalizer normalizer, DiffBuilder diffBuilder, LawStore lawStore,
                         ObservationRegistry observations) {
        this.normalizer = normalizer;
        this.diffBuilder = diffBuilder;
        this.lawStore = lawStore;
        this.observations = observations == null ? ObservationRegistry.NOOP : observations;
    }

    /**
     * pending {@code RawLaw}(+ 기준선, 제정이면 null) → normalize → diff → revision 이 바뀐 것만 upsert.
     * 시행예정본과 (있으면) 시행중본을 모두 저장한다.
     */
    public IngestResult store(RawLaw pendingRaw, RawLaw baselineRaw) {
        return Observation.createNotStarted(Obs.INGEST, observations).observe(() -> {
            Law pending = normalizer.normalize(pendingRaw);
            Law baseline = baselineRaw == null ? null : normalizer.normalize(baselineRaw);
            Law diffed = diffBuilder.build(pending, baseline)
                    .withRevision(pending.revision() + "+" + (baseline == null ? "none" : baseline.revision()));

            boolean pendingChanged = upsertIfChanged(diffed);   // 시행예정 정본(diff 포함)
            if (baseline != null) {
                upsertIfChanged(baseline);                       // 시행중 정본(= diff 기준선)
            }
            return new IngestResult(diffed.lawId(), diffed.effectiveDate(), diffed.changedArticles().size(),
                    baseline != null, baseline == null ? null : baseline.effectiveDate(), pendingChanged);
        });
    }

    /** 저장된 revision·상태와 같으면 쓰지 않는다(멱등·불필요한 색인 방지). 썼으면 true. */
    private boolean upsertIfChanged(Law law) {
        boolean same = lawStore.revisionOf(law.lawId(), law.effectiveDate(), law.status())
                .map(law.revision()::equals)
                .orElse(false);
        if (!same) {
            lawStore.upsert(law);
        }
        return !same;
    }

    /**
     * @param baselineEffectiveDate 기준선(시행중본)의 시행일. 제정이면 null
     * @param pendingChanged        시행예정 행에 쓰기가 일어났는가 — true 면 호출자가 색인을 등록한다
     */
    public record IngestResult(String lawId, LocalDate effectiveDate, int changedCount, boolean hasBaseline,
                               LocalDate baselineEffectiveDate, boolean pendingChanged) {}
}
