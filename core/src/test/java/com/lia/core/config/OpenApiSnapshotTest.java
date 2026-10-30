package com.lia.core.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenAPI 계약 스냅샷 — {@code /v3/api-docs}가 레포 {@code api/openapi.json}과 같아야 한다.
 * 의도한 계약 변경이면 {@code OPENAPI_UPDATE=true ./gradlew test --tests "*OpenApiSnapshotTest" --rerun-tasks}로 갱신하고
 * 웹 TS 타입을 다시 생성한다(OPENAPI_UPDATE 는 gradle 입력이 아니라 UP-TO-DATE 면 테스트가 안 돈다 — {@code --rerun-tasks} 필수). 파일이 없으면 처음 한 번 만든다. DB 불필요(전체 컨텍스트, 지연 연결).
 */
@SpringBootTest
class OpenApiSnapshotTest {

    static final Path SNAPSHOT = Path.of("..", "api", "openapi.json");   // gradle 작업 디렉터리 = core/

    @Autowired WebApplicationContext context;

    @Test
    @DisplayName("/v3/api-docs = api/openapi.json (줄바꿈 무시)")
    void 계약_스냅샷() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String raw = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonMapper json = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        String current = json.writeValueAsString(json.readTree(raw)).replace("\r\n", "\n") + "\n";

        if ("true".equals(System.getenv("OPENAPI_UPDATE")) || !Files.exists(SNAPSHOT)) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, current, StandardCharsets.UTF_8);
        }
        String saved = Files.readString(SNAPSHOT, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(saved, current,
                "API 계약이 바뀌었다 — 의도한 변경이면 OPENAPI_UPDATE=true 로 스냅샷을 갱신하고 웹 타입을 재생성하라");
        assertTrue(current.contains("/api/v1/analyses") && current.contains("/api/v1/data-status"));
        assertTrue(current.contains("/api/v1/laws/{lawId}/{effectiveDate}") && current.contains("LawDetailResponse"));
        assertTrue(current.contains("LawSummary") && current.contains("SummaryClaim"));
        assertTrue(current.contains("/api/v1/laws/trending") && current.contains("TrendingPage"));
        assertTrue(current.contains("AnalysisApiResponse"));
        assertTrue(current.contains("/api/v1/bookmarks/{lawId}") && current.contains("BookmarkListResponse"));
        assertTrue(current.contains("/api/v1/history/{id}") && current.contains("HistoryDetailResponse"));
    }
}
