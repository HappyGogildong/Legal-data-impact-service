package com.lia.core.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.lia.core.auth.CurrentUser;
import com.lia.core.testsupport.InMemoryAccountStore;
import com.lia.core.testsupport.InMemoryUserProfileStore;

/** Profile API 웹 슬라이스 — 동의 전 409·수정·조회(userId 없음)·파기·검증 400·인증 401. */
@SpringJUnitWebConfig(ApiSliceTestConfig.class)
class ProfileApiTest {

    static final String FULL = """
            {"purposes":["생활·주거","관심사 모니터링"],"age":29,"occupation":"사무",
             "employmentType":"무직·은퇴","householdType":"부부+자녀","housingType":"전세","regionSido":"서울특별시"}
            """;

    @Autowired InMemoryUserProfileStore profiles;
    @Autowired InMemoryAccountStore accounts;
    MockMvc mvc;
    UUID userId;

    @BeforeEach
    void setup(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // 세션은 실제 계정을 가리켜야 한다 — 없는 계정이면 StaleSessionFilter 가 낡은 세션으로 파기한다
        userId = accounts.findOrCreate("kakao", UUID.randomUUID().toString()).userId();
    }

    /** 로그인 + 세션 userId. */
    private MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request) {
        return request.with(user("u")).sessionAttr(CurrentUser.SESSION_KEY, userId);
    }

    private MockHttpServletRequestBuilder putProfile(String json) {
        return signedIn(put("/api/v1/profile")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    @DisplayName("동의 전 PUT → 409 consent_required")
    void 동의전_409() throws Exception {
        mvc.perform(putProfile(FULL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("consent_required"));
    }

    @Test
    @DisplayName("동의 후 PUT → 200 라벨 그대로, GET 에 userId 없음")
    void 동의후_수정_조회() throws Exception {
        profiles.recordConsent(userId, ApiSliceTestConfig.POLICY);

        mvc.perform(putProfile(FULL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupation").value("사무"))
                .andExpect(jsonPath("$.employmentType").value("무직·은퇴"))
                .andExpect(jsonPath("$.householdType").value("부부+자녀"))
                .andExpect(jsonPath("$.purposes[0]").value("생활·주거"))
                .andExpect(jsonPath("$.purposes[1]").value("관심사 모니터링"));

        mvc.perform(signedIn(get("/api/v1/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").value(29))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    @DisplayName("옛 버전 동의 → PUT 409(재동의 필요)")
    void 옛버전동의_409() throws Exception {
        profiles.recordConsent(userId, "old-version");

        mvc.perform(putProfile(FULL)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("동의만 한 GET → 200 빈 속성(purposes [], 나머지 null)")
    void 동의만_빈속성() throws Exception {
        profiles.recordConsent(userId, ApiSliceTestConfig.POLICY);

        mvc.perform(signedIn(get("/api/v1/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purposes").isEmpty())
                .andExpect(jsonPath("$.age").isEmpty())
                .andExpect(jsonPath("$.regionSido").isEmpty());
    }

    @Test
    @DisplayName("GET 미존재 → 404")
    void 미존재_404() throws Exception {
        mvc.perform(signedIn(get("/api/v1/profile"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE → 204, 이후 GET 404")
    void 파기_204() throws Exception {
        profiles.recordConsent(userId, ApiSliceTestConfig.POLICY);

        mvc.perform(signedIn(delete("/api/v1/profile")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(signedIn(get("/api/v1/profile"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("모르는 라벨·범위 밖 age → 400 bad_request")
    void 검증_400() throws Exception {
        profiles.recordConsent(userId, ApiSliceTestConfig.POLICY);

        mvc.perform(putProfile("{\"occupation\":\"개발자\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
        mvc.perform(putProfile("{\"regionSido\":\"서울\"}")).andExpect(status().isBadRequest());
        mvc.perform(putProfile("{\"age\":13}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 기기에서 계정이 삭제된 세션 → PUT·GET 모두 401(409·404 로 보이지 않음)")
    void 계정삭제된세션_401() throws Exception {
        profiles.recordConsent(userId, ApiSliceTestConfig.POLICY);
        accounts.delete(userId);
        profiles.delete(userId);   // 실 DB 의 FK cascade 흉내

        mvc.perform(putProfile(FULL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
        mvc.perform(signedIn(get("/api/v1/profile"))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("익명 → 401, 인증됐지만 세션 userId 없음 → 401")
    void 인증_401() throws Exception {
        mvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profile").with(user("u"))).andExpect(status().isUnauthorized());
    }
}
