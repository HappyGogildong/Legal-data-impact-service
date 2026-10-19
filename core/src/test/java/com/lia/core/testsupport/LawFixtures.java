package com.lia.core.testsupport;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.ObjectMapper;

import com.lia.core.pipeline.connector.RawLaw;

/** 번들 법령 fixture(주택법) — 본문 JSON 을 RawLaw 로. 식별값은 Normalizer 가 본문에서 읽는다. */
public final class LawFixtures {

    public static final String HOUSING_PENDING = "samples/housing-act.json";
    public static final String HOUSING_BASELINE = "samples/housing-act-baseline.json";
    public static final String HOUSING_LAW_ID = "001809";
    public static final String HOUSING_MST = "283191";
    public static final LocalDate HOUSING_EFFECTIVE = LocalDate.of(2026, 8, 4);
    public static final LocalDate HOUSING_BASELINE_EFFECTIVE = LocalDate.of(2020, 1, 1);

    private static final ObjectMapper JSON = new ObjectMapper();

    private LawFixtures() {}

    public static RawLaw raw(String classpath, String status) {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = JSON.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    LinkedHashMap.class);
            return new RawLaw(null, null, null, status, null, null, null, root);
        } catch (Exception e) {
            throw new IllegalStateException("fixture 로드 실패: " + classpath, e);
        }
    }
}
