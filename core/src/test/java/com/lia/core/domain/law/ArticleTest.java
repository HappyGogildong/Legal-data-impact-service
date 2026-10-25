package com.lia.core.domain.law;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.lia.core.domain.law.Article.ChangeType;

class ArticleTest {

    private static Article articleNo(String no) {
        return new Article(no, null, "", false, ChangeType.없음, null, null, null, true, null);
    }

    @Test
    void 조문번호를_인용_라벨로_바꾼다() {
        assertEquals("제18조", Article.labelOf("18"));
        assertEquals("제105조의2", Article.labelOf("105의2"));
        assertEquals("제7조의3", Article.labelOf("7의3"));
    }

    @Test
    void 번호가_없으면_라벨은_빈_문자열이다() {
        assertEquals("", Article.labelOf(null));
        assertEquals("", articleNo(null).label());
    }

    @Test
    void 인스턴스_라벨은_정적_라벨과_같다() {
        assertEquals(Article.labelOf("7의3"), articleNo("7의3").label());
    }
}
