package com.placement.pms;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;

/** Creates the initial placement-officer account once, when the database is empty. */
@Configuration
public class SeedDataConfig {
    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Bean
    CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder encoder) {
        return arguments -> {
            if (users.findByEmail(adminEmail).isEmpty()) {
                User admin = new User();
                admin.email = adminEmail;
                admin.password = encoder.encode(adminPassword);
                admin.role = Role.ROLE_ADMIN;
                users.save(admin);
            }
        };
    }
}
