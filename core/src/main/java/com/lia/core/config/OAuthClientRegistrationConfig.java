package com.lia.core.config;

import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

@Configuration
public class OAuthClientRegistrationConfig {

    static Map<String, ClientRegistration> registrations(Environment environment) {
        return Map.of();
    }
}
