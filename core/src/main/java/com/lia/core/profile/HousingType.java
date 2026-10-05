package com.lia.core.profile;

/** 주거형태 — 주거 법령 영향에 직결. */
public enum HousingType implements Labeled {
    자가("자가"), 전세("전세"), 월세("월세"), 기타("기타");

    private final String label;

    HousingType(String label) { this.label = label; }

    @Override
    public String label() { return label; }
}
