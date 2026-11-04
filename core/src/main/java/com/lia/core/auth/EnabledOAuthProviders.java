package com.lia.core.auth;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class EnabledOAuthProviders {

    public List<String> ids() {
        return List.of();
    }
}
