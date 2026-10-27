package com.lia.core.pipeline.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 명령줄 해석 — 비옵션 인자(명령·인자) + 옵션 이름(--full·--dry-run). Spring 옵션은 무시. */
class JobCommandTest {

    @Test
    @DisplayName("run · run --full · run --dry-run")
    void run() {
        assertEquals(new JobCommand(JobCommand.Type.RUN, false, false, null, null, null),
                JobCommand.parse(List.of("run"), Set.of("spring.profiles.active")));
        JobCommand full = JobCommand.parse(List.of("run"), Set.of("full", "dry-run"));
        assertTrue(full.full());
        assertTrue(full.dryRun());
        assertEquals("run --full --dry-run", full.label());
    }

    @Test
    @DisplayName("sync <lawId> <efYd> [mst]")
    void sync() {
        JobCommand withoutMst = JobCommand.parse(List.of("sync", "001809", "2026-08-04"), Set.of());
        assertEquals(JobCommand.Type.SYNC, withoutMst.type());
        assertEquals("001809", withoutMst.lawId());
        assertEquals(LocalDate.of(2026, 8, 4), withoutMst.effectiveDate());
        assertNull(withoutMst.mst());
        assertEquals("sync 001809 2026-08-04", withoutMst.label());

        assertEquals("283191", JobCommand.parse(List.of("sync", "001809", "2026-08-04", "283191"), Set.of()).mst());
    }

    @Test
    @DisplayName("index · reindex-all · requeue-dead")
    void 기타명령() {
        assertEquals(JobCommand.Type.INDEX, JobCommand.parse(List.of("index"), Set.of()).type());
        assertEquals("index", JobCommand.parse(List.of("index"), Set.of()).label());
        assertEquals(JobCommand.Type.REINDEX_ALL, JobCommand.parse(List.of("reindex-all"), Set.of()).type());
        assertEquals(JobCommand.Type.REQUEUE_DEAD, JobCommand.parse(List.of("requeue-dead"), Set.of()).type());
    }

    @Test
    @DisplayName("summarize 명령")
    void summarize_명령() {
        JobCommand command = JobCommand.parse(List.of("summarize"), Set.of());
        assertEquals(JobCommand.Type.SUMMARIZE, command.type());
        assertEquals("summarize", command.label());
        assertFalse(command.dryRun());
    }

    @Test
    @DisplayName("summarize --dry-run — 시험 실행 표기")
    void summarize_시험실행() {
        JobCommand command = JobCommand.parse(List.of("summarize"), Set.of("dry-run"));
        assertEquals(JobCommand.Type.SUMMARIZE, command.type());
        assertTrue(command.dryRun());
        assertEquals("summarize --dry-run", command.label());
    }

    @Test
    @DisplayName("명령 없음·모르는 명령·sync 인자 부족·잘못된 날짜 → IllegalArgumentException")
    void 잘못된입력() {
        assertThrows(IllegalArgumentException.class, () -> JobCommand.parse(List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> JobCommand.parse(List.of("fly"), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> JobCommand.parse(List.of("sync", "001809"), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> JobCommand.parse(List.of("sync", "001809", "2026/08/04"), Set.of()));
    }
}
