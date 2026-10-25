package com.lia.core.store;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 목록 커서 — 불투명 문자열 왕복과 잘못된 입력(→ 400 이 되도록 IllegalArgumentException). */
class LawCatalogCursorTest {

    private static String base64(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("encode → decode 왕복")
    void 왕복() {
        LawCatalogStore.Cursor cursor = new LawCatalogStore.Cursor(LocalDate.of(2026, 11, 1), "001809");

        assertEquals(cursor, LawCatalogStore.Cursor.decode(cursor.encode()));
    }

    @Test
    @DisplayName("base64 아님·구분자 없음·빈 lawId·날짜 아님 → IllegalArgumentException")
    void 잘못된_커서() {
        for (String bad : new String[] {"!!!", base64("2026-11-01"), base64("2026-11-01|"), base64("어제|001809")}) {
            assertThrows(IllegalArgumentException.class, () -> LawCatalogStore.Cursor.decode(bad), bad);
        }
    }
}
