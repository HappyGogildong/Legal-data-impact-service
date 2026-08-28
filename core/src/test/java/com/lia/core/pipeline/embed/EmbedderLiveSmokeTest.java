package com.lia.core.pipeline.embed;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import com.lia.core.pipeline.embed.Embedder.Mode;

/**
 * 실 OpenAI 임베딩 API 스모크 — <b>비용이 발생</b>한다. 기본 {@code ./gradlew test} 에서는
 * 절대 돌지 않는다: {@code OPENAI_API_KEY} 가 있어도 <b>명시적 옵트인</b>이 있어야 실행된다.
 * 사용자가 직접:
 * <pre>LIA_EMBED_LIVE=1 ./gradlew test --tests "*EmbedderLiveSmokeTest"</pre>
 * (IntelliJ면 Run Config 환경변수에 {@code LIA_EMBED_LIVE=1}).
 *
 * <p>단위 테스트는 목이라 "우리가 이해한 계약"만 본다. 여기서는 <b>실제 응답</b>이 dim 1536·
 * 단위 정규화로 떨어지는지, 같은 텍스트가 안정적으로 나오는지 실측한다.
 */
@SpringBootTest
class EmbedderLiveSmokeTest {

    @Autowired Embedder embedder;
    @Autowired Environment env;

    boolean hasKey() {
        String key = env.getProperty("spring.ai.openai.api-key");
        return key != null && !key.isBlank();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "LIA_EMBED_LIVE", matches = "(?i)(1|true|yes)")  // 명시적 옵트인(과금 방지)
    @EnabledIf("hasKey")
    void 실제_임베딩이_dim_1536_단위벡터로_떨어진다() {
        List<float[]> out = embedder.embed(
                List.of("주택법 제18조 사업계획의 통합심의", "근로기준법 연차 유급휴가"), Mode.PASSAGE);

        assertEquals(2, out.size());
        assertEquals(1536, embedder.dim());
        for (float[] v : out) {
            assertEquals(1536, v.length, "실 응답 차원이 설정과 다르다");
            assertEquals(1.0, norm(v), 1e-5, "단위 정규화 아님");
        }
        System.out.printf("[live] 임베딩 %d건 · dim %d · |v0|=%.6f%n", out.size(), out.get(0).length, norm(out.get(0)));

        // 결정론 — 같은 텍스트는 사실상 동일 벡터
        float[] again = embedder.embed("주택법 제18조 사업계획의 통합심의", Mode.PASSAGE);
        assertArrayEquals(out.get(0), again, 1e-4f, "같은 입력의 임베딩이 흔들린다");
    }

    private static double norm(float[] v) {
        double s = 0;
        for (float x : v) s += x * (double) x;
        return Math.sqrt(s);
    }
}
