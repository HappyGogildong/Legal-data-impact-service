package com.lia.core.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI 문서 머리(D64) — 웹 TS 타입 생성의 원천. 경로·설정은 application.yml 의 springdoc.
 * servers 는 같은 출처 상대 경로({@code /}) 하나로 고정 — 요청 호스트로 만들면 스냅샷이 환경에 묶인다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI liaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("LIA API")
                .version("v1")
                .description("입법 영향 분석기 공개 API — 계약 SSOT: docs/mvp/service-api-spec.md"))
                .servers(List.of(new Server().url("/")));
    }
}
