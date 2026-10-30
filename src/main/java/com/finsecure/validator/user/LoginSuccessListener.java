package com.finsecure.validator.user;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class LoginSuccessListener {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessListener.class);

    private final AppUserRepository repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public LoginSuccessListener(AppUserRepository repository, MeterRegistry meterRegistry, Clock clock) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        repository.findByUsername(username).ifPresent(user -> user.setLastLoginAt(Instant.now(clock)));
        meterRegistry.counter("finsecure.logins", "result", "success").increment();
        log.info("User '{}' signed in", username);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        meterRegistry.counter("finsecure.logins", "result", "failure").increment();
        log.warn("Failed sign-in attempt for '{}'", event.getAuthentication().getName());
    }
}
