package com.placement.pms;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Creates the initial placement-officer account once, when the database is empty. */
@Configuration
public class SeedDataConfig {

    @Bean
    CommandLineRunner seedAdmin(UserRepository users, PasswordEncoder encoder) {
        return arguments -> {
            if (users.findByEmail("admin@placement.edu").isEmpty()) {
                User admin = new User();
                admin.email = "admin@placement.edu";
                admin.password = encoder.encode("Admin@123");
                admin.role = Role.ROLE_ADMIN;
                users.save(admin);
            }
        };
    }
}
