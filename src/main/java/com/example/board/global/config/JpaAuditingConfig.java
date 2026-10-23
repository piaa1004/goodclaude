package com.example.board.global.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 메인 클래스에 두면 @WebMvcTest가 "JPA metamodel must not be empty"로 실패하므로 분리한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * PostgreSQL TIMESTAMPTZ 정밀도(마이크로초)에 맞춰 자른다. 그대로 두면 저장 직후 응답(나노초)과
     * 이후 조회 값(DB에서 마이크로초로 반올림)이 달라진다.
     */
    @Bean
    DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
