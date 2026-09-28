package com.lia.core.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lia.core.application.profile.ProfileUseCase;
import com.lia.core.auth.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code /api/v1/profile} — 프로필 속성 조회·수정·파기([[service-api-spec]] §3.4). <b>HTTP 경계만</b>:
 * 세션 userId → {@link ConsentController} 위임. 동의는 여기서 받지 않는다.
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileUseCase profiles;

    public ProfileController(ProfileUseCase profiles) {
        this.profiles = profiles;
    }

    /** 속성 전체 교체. 현재 버전 동의 없으면 409(ApiExceptionHandler). */
    @PutMapping
    public ProfileResponse update(@RequestBody ProfileApiRequest body, HttpServletRequest request) {
        return ProfileResponse.from(profiles.update(CurrentUser.require(request), body.toProfile()));
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> get(HttpServletRequest request) {
        return ResponseEntity.of(profiles.find(CurrentUser.require(request)).map(ProfileResponse::from));
    }

    /** 파기 = 프로필 동의 철회. 멱등. */
    @DeleteMapping
    public ResponseEntity<Void> delete(HttpServletRequest request) {
        profiles.delete(CurrentUser.require(request));
        return ResponseEntity.noContent().build();
    }
}
