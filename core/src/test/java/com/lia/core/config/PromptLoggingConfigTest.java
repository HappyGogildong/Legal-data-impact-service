package com.lia.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 프롬프트(프로필·질문)를 관측 데이터로 남기지 않는다(D41·D69) — 설정 파일 고정. */
class PromptLoggingConfigTest {

    @Test
    void 프롬프트와_응답_기록이_명시적으로_꺼져_있다() throws IOException {
        String yml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(yml.contains("log-prompt: false"));
        assertTrue(yml.contains("log-completion: false"));
        assertFalse(yml.contains("log-prompt: true"));
        assertTrue(yml.contains("log-query-response: false"));   // 벡터 검색어(프로필 라벨 포함 가능)
        assertFalse(yml.contains("log-query-response: true"));
    }
}
