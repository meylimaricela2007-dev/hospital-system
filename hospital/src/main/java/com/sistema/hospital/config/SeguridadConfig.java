package com.sistema.hospital.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * El pom.xml solo trae "spring-security-crypto" (la librería que encripta
 * contraseñas), no el starter completo de Spring Security. Por eso nadie
 * registra automáticamente un PasswordEncoder como @Bean: lo hacemos aquí,
 * una sola vez, para poder inyectarlo con @Autowired donde se necesite.
 */
@Configuration
public class



SeguridadConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
