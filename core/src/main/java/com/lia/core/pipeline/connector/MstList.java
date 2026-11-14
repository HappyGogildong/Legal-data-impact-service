package com.lia.core.pipeline.connector;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 한 버전(법령ID, 시행일)의 공포본 MST 목록 표기(D85) — 숫자 오름차순 쉼표 목록("280111,284089").
 * 작업 큐·저장 정본의 {@code mst} 가 이 표기를 쓰고, 탐색은 이 문자열이 달라지면 다시 SYNC 한다.
 */
public final class MstList {

    /** 숫자 순(숫자가 아니면 뒤로), 같으면 문자열 순 — 표기가 결정적이어야 변경 감지가 흔들리지 않는다. */
    public static final Comparator<String> ORDER = Comparator.comparingLong(MstList::number)
            .thenComparing(Comparator.naturalOrder());

    private MstList() {}

    /** 중복을 빼고 정렬해 쉼표로 잇는다. */
    public static String join(Collection<String> msts) {
        return msts.stream().map(String::strip).distinct().sorted(ORDER).collect(Collectors.joining(","));
    }

    /** {@link #join} 의 역. 공백·빈 항목은 버린다. */
    public static List<String> split(String msts) {
        if (msts == null || msts.isBlank()) return List.of();
        return Arrays.stream(msts.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static long number(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }
}
