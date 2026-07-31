package com.lia.core.pipeline.connector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lia.core.config.LiaSourceProperties;

/**
 * 열린국회 실 API 스모크 (이슈 #15) — 키가 주입된 환경에서만 실행된다.
 *
 * <p>Python 스모크(pipeline/scripts/smoke_assembly.py)와 동일한 결과를 Java 경로에서
 * 재현하는지 확인한다. 검증 포인트: AGE 필수 파라미터, 봉투 파싱, 필드 매핑.
 * 키가 없으면(CI 등) 조용히 비활성화된다.
 */
@SpringBootTest
class AssemblyLiveSmokeTest {

    @Autowired
    AssemblyBillsConnector connector;

    @Autowired
    LiaSourceProperties props;

    boolean hasKey() {
        String k = props.assembly().apiKey();
        return k != null && !k.isBlank();
    }

    @Test
    @EnabledIf("hasKey")
    void 법안_검색이_실제_결과를_반환한다() {
        List<RawBill> bills = connector.search("주택임대차", 5);

        assertFalse(bills.isEmpty(), "검색 결과 0건 — AGE 파라미터/서비스 ID 확인 필요");
        System.out.printf("[live] search('주택임대차') → %d건%n", bills.size());
        for (RawBill b : bills) {
            System.out.printf("   - [%s] %s%n", b.billNo(), b.title());
            assertEquals("assembly", b.sourceType());
            assertNotNull(b.billNo(), "의안번호 누락 — 필드 매핑 확인");
            assertTrue(b.title().contains("주택임대차"), "검색어와 무관한 결과: " + b.title());
        }
    }

    @Test
    @EnabledIf("hasKey")
    void 의안번호_정확조회가_동작한다() {
        List<RawBill> bills = connector.search("주택임대차", 1);
        String billNo = bills.get(0).billNo();

        RawBill found = connector.getByBillNo(billNo);
        assertNotNull(found, "의안번호 " + billNo + " 정확조회 실패");
        assertEquals(billNo, found.billNo());
        System.out.printf("[live] getByBillNo(%s) → %s%n", billNo, found.title());
    }
}
