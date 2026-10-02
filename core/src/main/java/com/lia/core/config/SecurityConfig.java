package com.lia.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.lia.core.auth.AccountStore;
import com.lia.core.auth.OAuth2LoginSuccessHandler;
import com.lia.core.auth.StaleSessionFilter;

@Configuration
public class SecurityConfig {

  /** /api/** 미인증 401 — 본문을 ApiExceptionHandler 의 {error, message} 와 같은 모양으로(SPA 는 401 한 가지만 처리). */
  private static final AuthenticationEntryPoint API_UNAUTHENTICATED = (request, response, exception) -> {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"error\":\"unauthenticated\",\"message\":\"로그인이 필요합니다.\"}");
  };

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      OAuth2LoginSuccessHandler loginSuccessHandler,
      AccountStore accountStore,
      // oauth 프로파일일 때만 이 빈이 존재 → Optional 로 받아 조건부 처리
      ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {

    http
        // 1) 인가 규칙 — Auth.md 표대로. 위에서 아래로 첫 매칭이 이긴다.
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
            .requestMatchers("/oauth2/**", "/login/**", "/logout").permitAll()
            // 오류 포워드(ERROR 디스패치)도 인가 대상 — 막으면 공개 API의 400/500 이 익명에게 403 으로 바뀐다
            .requestMatchers("/error").permitAll()
            .requestMatchers("/api/v1/laws/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/analyses").permitAll()
            // 문서용(Auth.md 표 미러) — 실제 보호는 아래 anyRequest 가 한다
            .requestMatchers("/api/v1/profile/**").authenticated()
            .anyRequest().authenticated())

        // 2) API 미인증 → 401. 없으면 기본 403(oauth 프로파일은 302 /login)이라 SPA가 구분 못 한다.
        .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
            API_UNAUTHENTICATED,
            PathPatternRequestMatcher.withDefaults().matcher("/api/**")))

        // 3) CSRF — 세션-쿠키 인증이라 켠다. spa(): XSRF-TOKEN 쿠키(JS 읽기 가능) + 원문 헤더 토큰 허용.
        //    기본 Xor 핸들러는 마스킹 토큰을 요구해 SPA 가 쿠키값을 그대로 보내면 403.
        // ponytail: analyses 예외는 "세션 인증 안 씀" 전제의 임시 조치. 슬라이스 ③에서 이 엔드포인트가
        //           세션 userId 를 읽게 되면 예외를 제거하거나 익명/인증 경로를 분리한다.
        .csrf(csrf -> csrf
            .spa()
            .ignoringRequestMatchers("/api/v1/analyses"))

        // 4) 로그아웃 — 세션 무효화 + 쿠키 삭제 (D41 파기). 기본 URL: POST /logout.
        //    SPA 는 API 루트가 아니라 프론트에 있으므로 리다이렉트 대신 204.
        .logout(logout -> logout
            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID"))

        // 5) 삭제된 계정을 가리키는 세션(다른 기기에서 계정 삭제) — 세션에서 SecurityContext 를 읽은 직후,
        //    인가 전에 파기하고 익명으로 진행시킨다. 보호 API 는 2)의 401, 공개 API 는 정상 처리.
        .addFilterAfter(new StaleSessionFilter(accountStore), SecurityContextHolderFilter.class);

    // 6) 소셜 로그인 — ClientRegistrationRepository 빈이 있을 때(=oauth 프로파일)만 붙인다.
    //    없는데 oauth2Login() 을 부르면 부팅 자체가 깨진다.
    //    성공 시 loginSuccessHandler 가 provider+subject→userId 매핑 후 세션에 심는다.
    clientRegistrations.ifAvailable(repo -> {
      try {
        http.oauth2Login(oauth -> oauth.successHandler(loginSuccessHandler));
      } catch (Exception e) {
        throw new IllegalStateException("oauth2Login 구성 실패", e);
      }
    });

    return http.build();
  }
}
