package com.lia.core.pipeline.analyze;

import java.util.Objects;

import com.lia.core.domain.law.Law;
import com.lia.core.pipeline.plan.QueryType;
import com.lia.core.profile.UserProfile;

/**
 * 분석 1건 요청 — 해소된 시행예정 정본 + (있으면) 기준선. 검색이 아니라 <b>정확 조회된 정본</b>이 들어온다.
 *
 * <p>우리가 산출한 판정 타입이라 <b>생성자가 불변식을 강제</b>한다(dimension·law 필수). 덕분에
 * {@link ContextBuilder}는 방어 없이 assembly에만 집중한다. {@code baseline}은 제정이면 null(정상, D42).
 * {@code profile}·{@code question}은 Layer B(D69)만 — 프로필은 Layer B 에 필수이고 Layer A 엔 넣지 않는다
 * (Layer A 는 법령당 같은 답이라 선계산·재사용한다, D67). Layer B 결합 호출(IMPACT·ACTION 한 번)은 dimension=IMPACT.
 *
 * @param dimension 분석 차원 · @param law 시행예정 정본 · @param baseline 시행중본(제정이면 null)
 * @param profile 프롬프트에 넣을 프로필 속성(Layer B 만, userId 없음) · @param question 사용자 질문(Layer B 만)
 */
public record AnalyzeRequest(QueryType dimension, Law law, Law baseline, UserProfile profile, String question) {

    public AnalyzeRequest {
        Objects.requireNonNull(dimension, "dimension은 필수다.");
        Objects.requireNonNull(law, "law는 필수다 — 해소된 정본이 들어와야 한다.");
        if (dimension.isLayerB() && profile == null) {
            throw new IllegalArgumentException("Layer B 요청에는 프로필이 필요하다: " + dimension);
        }
        if (!dimension.isLayerB() && profile != null) {
            throw new IllegalArgumentException("Layer A 요청에는 프로필을 넣지 않는다: " + dimension);
        }
    }

    /** Layer A — 프로필·질문 없음. */
    public AnalyzeRequest(QueryType dimension, Law law, Law baseline) {
        this(dimension, law, baseline, null, null);
    }
}
