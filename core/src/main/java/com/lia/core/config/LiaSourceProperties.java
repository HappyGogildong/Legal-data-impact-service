package com.lia.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 신뢰 출처 설정 (application.yml `lia.sources.*` ← 환경변수/.env).
 *
 * ⚠️ 이름 혼동 주의(내용 기준): assembly=의원발의(열린국회, ServiceKey),
 *    moleg=정부입법예고(법제처, OC=회원 이메일 아이디), law=현행법령(국가법령정보, OC).
 * Python 참조: pipeline/config.example.yaml
 */
@ConfigurationProperties(prefix = "lia.sources")
public record LiaSourceProperties(Assembly assembly, Moleg moleg, Law law) {

    public record Assembly(
            String apiKey,      // 열린국회 ServiceKey
            String service,     // 의원발의 법률안 목록 서비스 ID
            String age,         // 국회 대수(필수 파라미터) — 빠지면 ERROR-300
            String base,
            int pageSize,
            double timeout,
            int maxRetries
    ) {
        public Assembly {
            if (service == null || service.isBlank()) service = "nzmimeepazxkubdpn";
            if (age == null || age.isBlank()) age = "22";
            if (base == null || base.isBlank()) base = "https://open.assembly.go.kr/portal/openapi";
            if (pageSize <= 0) pageSize = 100;
            if (timeout <= 0) timeout = 20.0;
            if (maxRetries <= 0) maxRetries = 3;
        }
    }

    public record Moleg(String oc, String base) {}   // 법제처 입법예고 — OC 인증

    /** 국가법령정보 — OC 인증. eflaw(시행예정)·law(시행중) 양쪽을 한 커넥터가 쓴다(D42). */
    public record Law(String oc, String base, double timeout, int maxRetries) {
        public Law {
            if (base == null || base.isBlank()) base = "https://www.law.go.kr";
            if (timeout <= 0) timeout = 20.0;
            if (maxRetries <= 0) maxRetries = 3;
        }
    }
}
