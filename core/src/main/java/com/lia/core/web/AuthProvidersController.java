package com.lia.core.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;

import com.lia.core.auth.EnabledOAuthProviders;

/** {@code GET /api/v1/auth/providers} — 켜진 소셜 로그인 provider(D78). 웹 로그인 화면이 이 버튼만 그린다. 익명 공개. */
@RestController
@RequestMapping("/api/v1/auth/providers")
public class AuthProvidersController {

    private final EnabledOAuthProviders providers;

    public AuthProvidersController(EnabledOAuthProviders providers) {
        this.providers = providers;
    }

    @GetMapping
    @Operation(operationId = "authProviders")
    public ProvidersResponse get() {
        return new ProvidersResponse(providers.ids());
    }

    public record ProvidersResponse(List<String> providers) {}
}
