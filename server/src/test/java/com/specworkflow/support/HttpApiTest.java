package com.specworkflow.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 서버 HTTP API 테스트. 실제 포트로 서버를 띄우고, Testcontainers의 PostgreSQL·Redis와 테스트용 시계를 쓴다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "auth.jwt.secret=" + TestInfrastructure.JWT_SECRET,
            "spring.data.redis.timeout=500ms",
            "spring.data.redis.connect-timeout=500ms"
        })
@Import(TestInfrastructure.class)
public @interface HttpApiTest {}
