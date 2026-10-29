package com.lia.core.pipeline.discover;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 법령 발견 <b>실 왕복 스모크</b> — 실 임베딩 API로 질문을 임베딩해 실 시행예정 색인을 검색한다. <b>비용 발생</b>.
 * 기본 {@code ./gradlew test}에서 절대 안 돈다: 명시적 옵트인 {@code LIA_DISCOVERY_LIVE=1}(그때 임베딩 API 키 필요).
 * 사용자가 직접:
 * <pre>LIA_DISCOVERY_LIVE=1 ./gradlew test --tests "*LawDiscoveryLiveSmokeTest"</pre>
 *
 * <p>설정된 실 DB(시행예정 색인·정본이 적재된 로컬/클라우드 DB)를 쓴다 — 후보 정본은 {@code law_versions} 에서
 * 읽으므로 빈 Testcontainers DB 로는 의미 있는 검증이 안 된다(RagSearchLiveSmokeTest 와 다른 점).
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LIA_DISCOVERY_LIVE", matches = "1")
class LawDiscoveryLiveSmokeTest {

    @Autowired LawDiscovery discovery;

    @Test
    void 법령명_없는_질문으로_시행예정_후보를_찾는다() {
        DiscoveryResult result = discovery.discover("전세 세입자한테 바뀌는 법 있어?", Optional.empty());

        System.out.println(result.candidates().stream()
                .map(c -> c.title() + " | " + c.matched().label() + " | " + c.summary()).toList());
        assertTrue(result.candidates().stream().allMatch(c -> c.lawId() != null && c.effectiveDate() != null));
    }
}
