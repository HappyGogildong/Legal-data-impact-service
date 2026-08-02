package com.lia.core.pipeline.connector;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lia.core.config.LiaSourceProperties;

/**
 * 국가법령정보 실 API 스모크 (이슈 #11) — OC 가 주입된 환경에서만 실행된다.
 *
 * <p>MVP 수집 경로 전체를 한 번에 태운다: 시행예정 목록 → 본문 → 시행중 기준선.
 * 검증 포인트는 실측으로 확인된 함정들이다(D42).
 */
@SpringBootTest
class LawLiveSmokeTest {

    @Autowired
    LawConnector connector;

    @Autowired
    LiaSourceProperties props;

    boolean hasOc() {
        String oc = props.law().oc();
        return oc != null && !oc.isBlank();
    }

    private static final LocalDate FROM = LocalDate.now().plusDays(1);
    private static final LocalDate TO = FROM.plusYears(1);

    @Test
    @EnabledIf("hasOc")
    void 시행예정_법령_목록을_가져온다() {
        List<RawLaw> pending = connector.listPending(FROM, TO, 5);

        assertFalse(pending.isEmpty(), "시행예정 0건 — efYd 범위/OC 확인 필요");
        System.out.printf("[live] listPending(%s~%s) → %d건%n", FROM, TO, pending.size());
        for (RawLaw law : pending) {
            System.out.printf("   - [시행 %s] %s (공포 %s 제%s호, 법령ID %s)%n",
                    law.effectiveDate(), law.title(), law.promulgateDate(), law.promulgateNo(), law.lawId());

            assertEquals("시행예정", law.status(), "현행연혁코드가 시행예정이 아니다");
            assertNotNull(law.lawId(), "법령ID 누락 — 시행중본 연결이 불가능해진다");
            assertNotNull(law.mst(), "법령일련번호 누락 — 본문 조회가 불가능해진다");
            assertNotNull(law.effectiveDate(), "시행일자 누락");
            assertTrue(law.effectiveDate().isAfter(LocalDate.now()),
                    "이미 시행된 법령이 섞였다: " + law.title() + " " + law.effectiveDate());
        }
    }

    @Test
    @EnabledIf("hasOc")
    void 단건_조회에서도_목록_파싱이_깨지지_않는다() {
        // display=1 이면 law 가 배열이 아니라 객체로 온다(실측 함정)
        List<RawLaw> one = connector.listPending(FROM, TO, 1);

        assertEquals(1, one.size(), "단건 응답이 객체로 와서 누락됐다 — LawEnvelope.extractRows 확인");
        assertNotNull(one.get(0).lawId());
    }

    @Test
    @EnabledIf("hasOc")
    void 시행예정_본문에_조문과_개정문이_들어있다() {
        RawLaw head = connector.listPending(FROM, TO, 1).get(0);

        RawLaw body = connector.fetchPending(head.mst(), head.effectiveDate());

        assertTrue(body.hasBody(), "본문에 조문 블록이 없다");
        assertEquals(head.lawId(), body.lawId(), "목록↔본문의 법령ID가 어긋난다");

        List<Map<String, Object>> articles = LawEnvelope.articles(body.raw());
        List<Map<String, Object>> changed = articles.stream()
                .filter(a -> "Y".equalsIgnoreCase(String.valueOf(a.get("조문변경여부")))).toList();
        List<Map<String, Object>> addenda = LawEnvelope.addenda(body.raw()).stream()
                .filter(a -> body.promulgateNo().equals(LawEnvelope.str(a.get("부칙공포번호")))).toList();

        System.out.printf("[live] %s — 조문 %d개(변경 %d개), 이번 개정 부칙 %d개%n",
                body.title(), articles.size(), changed.size(), addenda.size());

        assertFalse(articles.isEmpty(), "조문단위가 비었다");
        assertTrue(changed.size() <= articles.size(), "변경 조문이 전체보다 많다");
        assertNotNull(LawEnvelope.str(body.raw().get("개정문")), "개정문 누락 — diff 근거가 사라진다");

        // 함정 3: 조문내용만 읽으면 본문이 빈다. 평탄화하면 실제 텍스트가 나와야 한다.
        String merged = LawEnvelope.text(articles.get(articles.size() - 1));
        assertFalse(merged.isBlank(), "조문 평탄화 결과가 비었다 — 항/호/목 병합 확인");
    }

    @Test
    @EnabledIf("hasOc")
    void 법령ID로_시행중_기준선을_가져온다() {
        RawLaw pending = connector.listPending(FROM, TO, 1).get(0);

        RawLaw current = connector.fetchCurrent(pending.lawId());

        assertNotNull(current, "시행중본 조회 실패");
        assertEquals(pending.lawId(), current.lawId(), "법령ID가 연결키로 동작하지 않는다");
        assertTrue(current.hasBody(), "시행중본에 조문이 없다");
        assertTrue(current.effectiveDate().isBefore(pending.effectiveDate().plusDays(1)),
                "기준선의 시행일이 시행예정본보다 늦다");

        System.out.printf("[live] 기준선: %s 시행 %s (공포 제%s호) ↔ 시행예정 %s (공포 제%s호)%n",
                current.title(), current.effectiveDate(), current.promulgateNo(),
                pending.effectiveDate(), pending.promulgateNo());
    }

    @Test
    @EnabledIf("hasOc")
    void 잘못된_OC는_예외로_드러난다() {
        // 국가법령정보는 인증 실패도 HTTP 200 으로 주므로 봉투 검사가 유일한 방어선이다.
        var bad = new LawConnector(org.springframework.web.client.RestClient.builder(),
                new LiaSourceProperties.Law("zzz_invalid_oc", props.law().base(), 20, 1));

        assertThrows(LawApiException.class, () -> bad.listPending(FROM, TO, 1));
    }
}
