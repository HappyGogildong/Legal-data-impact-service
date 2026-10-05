package com.lia.core.profile;

/** 고용형태. */
public enum EmploymentType implements Labeled {
    임금근로("임금근로"), 자영업("자영업"), 프리랜서("프리랜서"), 무직은퇴("무직·은퇴"), 학생("학생");

    private final String label;

    EmploymentType(String label) { this.label = label; }

    @Override
    public String label() { return label; }
}
