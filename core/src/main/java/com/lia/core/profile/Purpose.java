package com.lia.core.profile;

/** 이용 목적(다중 선택) — 개인화의 핵심 축(component-specs §2). */
public enum Purpose implements Labeled {
    생활주거("생활·주거"), 세금재정("세금·재정"), 근로고용("근로·고용"), 사업창업("사업·창업"),
    복지의료("복지·의료"), 교육양육("교육·양육"), 관심사모니터링("관심사 모니터링"), 기타("기타");

    private final String label;

    Purpose(String label) { this.label = label; }

    @Override
    public String label() { return label; }
}
