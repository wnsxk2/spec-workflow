package com.specworkflow.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트용 PostgreSQL·Redis 컨테이너와 테스트용 시계. 개발용 Docker Compose 데이터와는 완전히 분리된다.
 * 이미지 버전은 저장소 루트의 compose.yaml과 같게 맞춘다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    public static final String JWT_SECRET = "test-only-secret-key-that-is-at-least-32-characters";
    public static final String POSTGRES_IMAGE = "postgres:16.4-alpine";
    public static final String REDIS_IMAGE = "redis:7.4-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(6379);
    }

    @Bean
    @Primary
    TestClock testClock() {
        return new TestClock();
    }
}
