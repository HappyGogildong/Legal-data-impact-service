package com.lia.core.profile;

/** 가구형태. "1인"은 숫자로 시작해 상수명을 `일인`으로 둔다. */
public enum HouseholdType implements Labeled {
    일인("1인"), 부부("부부"), 부부자녀("부부+자녀"), 한부모("한부모"), 기타("기타");

    private final String label;

    HouseholdType(String label) { this.label = label; }

    @Override
    public String label() { return label; }
}
