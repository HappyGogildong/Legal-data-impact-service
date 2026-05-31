package com.lia.core.command;

/** 커맨드 실행에 필요한 선행 데이터 선언. 오케스트레이터가 채워 넣는다. */
public enum Requirement {
    BILL_FULL_TEXT,     // 의안 원문 로드
    CURRENT_LAW_DIFF,   // 현행법 대비 diff (baselineLawId 기준)
    USER_PERSONA        // 사용자 페르소나
}
