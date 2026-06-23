package com.folhetosmart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppConfig {

    /** Clock injetável (facilita testar lógica dependente da data). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
