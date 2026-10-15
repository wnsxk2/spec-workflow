package com.specworkflow.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Access Token 서명용 비밀 키. 코드가 아니라 실행 환경(AUTH_JWT_SECRET)으로 받는다.
 * 없거나 HS256에 쓰기에 짧으면 서버가 시작되지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "auth.jwt")
record JwtProperties(
        @NotBlank(message = "AUTH_JWT_SECRET 환경 변수로 Access Token 서명용 비밀 키를 넣어야 합니다")
        @Size(min = 32, message = "Access Token 서명용 비밀 키는 32자 이상이어야 합니다")
        String secret) {}
