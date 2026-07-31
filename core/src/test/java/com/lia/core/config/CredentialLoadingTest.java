package com.lia.core.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 자격증명 주입 경로 검증 (D39).
 *
 * <p>Spring Boot는 `.env`를 직접 읽지 않으므로, spring-dotenv(로컬) 또는
 * compose env_file(컨테이너)이 없으면 모든 키가 빈 문자열이 된다.
 * 이 테스트는 **바인딩 경로 자체**를 검증한다 — 값의 유무는 환경에 따라 다르므로
 * 단정하지 않고, 값이 있을 때 형식이 맞는지만 확인한다(CI에서 키 없이도 통과).
 */
@SpringBootTest
class CredentialLoadingTest {

    @Autowired
    LiaSourceProperties props;

    @Test
    void 프로퍼티_바인딩이_동작한다() {
        assertNotNull(props, "LiaSourceProperties 빈이 없음 — @ConfigurationPropertiesScan 확인");
        assertNotNull(props.assembly(), "sources.assembly 바인딩 실패");
        // 기본값은 record 컴팩트 생성자에서 채워진다
        assertEquals("nzmimeepazxkubdpn", props.assembly().service());
        assertEquals("22", props.assembly().age());
        assertTrue(props.assembly().base().contains("open.assembly.go.kr"));
        assertEquals(100, props.assembly().pageSize());
    }

    @Test
    void 자격증명이_주입되면_형식이_맞는다() {
        String key = props.assembly().apiKey();
        if (key == null || key.isBlank()) {
            System.out.println("[skip] ASSEMBLY_API_KEY 미주입 — .env 로드 또는 env_file 확인 필요");
            return;   // 키 없는 환경(CI)에서도 실패시키지 않음
        }
        assertTrue(key.length() >= 16, "열린국회 ServiceKey 길이가 비정상: " + key.length());
        System.out.printf("[ok] ASSEMBLY_API_KEY 주입됨 (%d자), MOLEG_OC=%s, LAW_OC=%s%n",
                key.length(),
                blankToMark(props.moleg().oc()), blankToMark(props.law().oc()));
    }

    private String blankToMark(String v) {
        return (v == null || v.isBlank()) ? "(미주입)" : "주입됨(" + v.length() + "자)";
    }
}
