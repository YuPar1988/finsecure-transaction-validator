package com.finsecure.validator.user;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.finsecure.validator.config.AppProperties;

/**
 * Creates the dashboard administrator on first start.
 * The password comes from ADMIN_PASSWORD (a Kubernetes Secret in the cluster). When it is not set,
 * a random password is generated and printed once so that no default credential exists.
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final AppUserRepository repository;
    private final PasswordEncoder encoder;
    private final AppProperties app;
    private final Clock clock;

    public AdminUserInitializer(AppUserRepository repository, PasswordEncoder encoder, AppProperties app, Clock clock) {
        this.repository = repository;
        this.encoder = encoder;
        this.app = app;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = app.admin().username();
        if (repository.existsByUsername(username)) {
            return;
        }

        String password = app.admin().password();
        boolean generated = password == null || password.isBlank();
        if (generated) {
            byte[] bytes = new byte[12];
            new SecureRandom().nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }

        try {
            repository.save(new AppUser(username, encoder.encode(password), "ADMIN", Instant.now(clock)));
        } catch (DataIntegrityViolationException ex) {
            return;
        }

        if (generated) {
            log.warn("ADMIN_PASSWORD not set. Generated password for '{}': {}", username, password);
        } else {
            log.info("Admin user '{}' created", username);
        }
    }
}
