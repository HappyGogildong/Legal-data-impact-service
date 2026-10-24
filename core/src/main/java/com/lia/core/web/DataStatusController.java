package com.lia.core.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.store.DataStatusStore;

/** {@code GET /api/v1/data-status} — 데이터 현황(익명 공개, [[service-api-spec]] §3.7). */
@RestController
@RequestMapping("/api/v1/data-status")
public class DataStatusController {

    private final DataStatusStore store;

    public DataStatusController(DataStatusStore store) {
        this.store = store;
    }

    @GetMapping
    public DataStatusStore.DataStatus get() {
        return store.read();
    }
}
