package com.lia.core.eval;

/**
 * 지표 하나 — 값과 분모. 분모가 작으면 한 문항이 값을 크게 흔들므로 함께 싣는다(D83).
 *
 * @param value 비율(0~1)
 * @param n     분모 — 그 지표를 잰 문항(또는 후보) 수
 */
public record Measure(double value, int n) {}
