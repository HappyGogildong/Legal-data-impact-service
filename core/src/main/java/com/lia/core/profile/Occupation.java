package com.lia.core.profile;

/** 직업군 대분류 — 자유 문자열이 아니라 닫힌 값(프롬프트 인젝션 경로 차단, D61). */
public enum Occupation implements Labeled {
    사무("사무"), 서비스("서비스"), 생산("생산"), 전문("전문"),
    자영("자영"), 농림어업("농림어업"), 학생("학생"), 무직("무직");

    private final String label;

    Occupation(String label) { this.label = label; }

    @Override
    public String label() { return label; }
}
