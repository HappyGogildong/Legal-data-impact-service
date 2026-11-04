package com.lia.core.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

/**
 * 소셜 로그인 등록(D60·D78) — {@code lia.oauth.*} 를 Boot 의 OAuth2 클라이언트 설정과 같은 모양으로 읽어
 * client-id 가 있는 provider 만 등록한다. 키가 구글만 있으면 구글만 켜지고, 나중에 키를 넣으면 재시작만으로 켜진다.
 */
@Configuration
@Profile("oauth")
@Conditional(OAuthClientsConfigured.class)
public class OAuthClientRegistrationConfig {

    static final String PREFIX = "lia.oauth";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(Environment environment) {
        return new InMemoryClientRegistrationRepository(List.copyOf(registrations(environment).values()));
    }

    static Map<String, ClientRegistration> registrations(Environment environment) {
        OAuth2ClientProperties properties = Binder.get(environment)
                .bind(PREFIX, Bindable.ofInstance(new OAuth2ClientProperties()))
                .orElseGet(OAuth2ClientProperties::new);
        properties.getRegistration().values().removeIf(registration -> !StringUtils.hasText(registration.getClientId()));
        return new OAuth2ClientPropertiesMapper(properties).asClientRegistrations();
    }
}
