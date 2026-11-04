package com.lia.core.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

/** 켜진 소셜 provider id(D78) — 실제 등록과 같은 소스(저장소 빈)에서 만든다. 순서 google·kakao·naver 고정. */
@Component
public class EnabledOAuthProviders {

    private static final List<String> ORDER = List.of("google", "kakao", "naver");

    private final List<String> ids;

    @Autowired
    public EnabledOAuthProviders(ObjectProvider<ClientRegistrationRepository> repository) {
        Set<String> registered = new HashSet<>();
        repository.ifAvailable(found -> {
            if (found instanceof Iterable<?> registrations) {
                for (Object registration : registrations) {
                    registered.add(((ClientRegistration) registration).getRegistrationId());
                }
            }
        });
        this.ids = ORDER.stream().filter(registered::contains).toList();
    }

    private EnabledOAuthProviders(List<String> ids) {
        this.ids = List.copyOf(ids);
    }

    /** 테스트·슬라이스용. */
    public static EnabledOAuthProviders of(List<String> ids) {
        return new EnabledOAuthProviders(ids);
    }

    public List<String> ids() {
        return ids;
    }
}
