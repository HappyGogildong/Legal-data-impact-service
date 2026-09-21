package com.lia.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.lia.core.auth.OAuth2LoginSuccessHandler;

@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      OAuth2LoginSuccessHandler loginSuccessHandler,
      // oauth 프로파일일 때만 이 빈이 존재 → Optional 로 받아 조건부 처리
      ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {

    http
        // 1) 인가 규칙 — Auth.md 표대로. 위에서 아래로 첫 매칭이 이긴다.
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
            .requestMatchers("/oauth2/**", "/login/**", "/logout").permitAll()
            .requestMatchers("/api/v1/laws/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/analyses").permitAll()
            .requestMatchers("/api/v1/profile/**").authenticated()
            .anyRequest().authenticated())

        // 2) CSRF — 세션-쿠키 인증이라 켠다. SPA 가 JS로 읽어 헤더에 싣도록 XSRF-TOKEN 쿠키.
        //    공개 무상태 POST(analyses)만 예외 — 세션 인증을 안 쓰므로 CSRF 대상 아님.
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .ignoringRequestMatchers("/api/v1/analyses"))

        // 3) 로그아웃 — 세션 무효화 + 쿠키 삭제 (D41 파기 요건). 기본 URL: POST /logout
        .logout(logout -> logout
            .logoutSuccessUrl("/")
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID"));

    // 4) 소셜 로그인 — ClientRegistrationRepository 빈이 있을 때(=oauth 프로파일)만 붙인다.
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
