package com.lia.core.profile;

/**
 * 라벨을 가진 프로필 enum 공통 — API·DB 값은 라벨 문자열 하나(D61).
 *
 * <p>상수명은 Java 식별자 제약(`·`·`+`·공백·숫자 시작) 때문에 라벨과 다를 수 있다.
 * 변환은 이 인터페이스의 두 정적 메서드로만 한다. 설계: docs/components/profile/UserProfile.md §라벨 규약
 */
public interface Labeled {

    String label();

    /** 라벨 → enum. null 은 null(선택 입력). 모르는 라벨은 400 이 되도록 IllegalArgumentException. */
    static <E extends Enum<E> & Labeled> E parse(Class<E> type, String label) {
        if (label == null) return null;
        for (E constant : type.getEnumConstants()) {
            if (constant.label().equals(label)) return constant;
        }
        throw new IllegalArgumentException(type.getSimpleName() + "에 없는 값입니다: " + label);
    }

    /** enum → 라벨. null 은 null. */
    static String labelOf(Labeled value) {
        return value == null ? null : value.label();
    }
}
