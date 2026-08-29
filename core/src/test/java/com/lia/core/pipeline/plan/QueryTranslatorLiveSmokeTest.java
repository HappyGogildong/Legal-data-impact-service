package com.lia.core.pipeline.plan;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 실 Haiku 번역 스모크 — <b>비용 발생</b>. 기본 {@code ./gradlew test}에서 절대 안 돈다:
 * <b>명시적 옵트인</b> {@code LIA_PLAN_LIVE=1}이 있어야 실행(그때 {@code ANTHROPIC_API_KEY} 필요).
 * 컨텍스트 로드 전 env 조건으로 스킵되므로 무키 실행에서도 안전. 사용자가 직접:
 * <pre>LIA_PLAN_LIVE=1 ./gradlew test --tests "*QueryTranslatorLiveSmokeTest"</pre>
 *
 * <p>단위는 Fake라 "우리가 이해한 계약"만 본다. 여기서는 실제 Haiku가 자연어를 쓸 만한 draft로
 * 번역하는지 실측한다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LIA_PLAN_LIVE", matches = "(?i)(1|true|yes)")
class QueryTranslatorLiveSmokeTest {

    @Autowired QueryTranslator translator;

    @Test
    void 법령질의는_법령의도로_번역된다() {
        AnalysisQueryDraft draft = translator.translate("주택법 제18조 뭐가 바뀌었어?");

        System.out.printf("[live] isLawQuery=%s targetKind=%s primary=%s types=%s lawName=%s art=%s%n",
                draft.isLawQuery(), draft.targetKind(), draft.primaryType(), draft.types(),
                draft.lawName(), draft.articleNo());

        assertTrue(draft.isLawQuery(), "법령 질의인데 isLawQuery=false");
        assertNotNull(draft.primaryType());
        assertFalse(draft.types().isEmpty(), "types가 비었다");
        assertEquals(AnalysisQueryDraft.TargetKind.REFERENCE, draft.targetKind(), "특정 법령 지목=REFERENCE");
        assertNotNull(draft.lawName(), "lawName 추출 실패");
    }

    @Test
    void 오프토픽은_비법령으로_번역된다() {
        AnalysisQueryDraft draft = translator.translate("오늘 점심 뭐 먹지");
        System.out.printf("[live] 오프토픽 isLawQuery=%s%n", draft.isLawQuery());
        assertFalse(draft.isLawQuery(), "오프토픽인데 isLawQuery=true");
    }
}
