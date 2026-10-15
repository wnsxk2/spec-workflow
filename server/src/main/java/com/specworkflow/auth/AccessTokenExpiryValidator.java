package com.specworkflow.auth;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** 서버의 시계로 Access Token의 만료를 확인한다. 만료 시각이 되면 거절하며, 여유 시간은 두지 않는다. */
class AccessTokenExpiryValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error EXPIRED =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Access Token이 만료되었습니다", null);

    private final Clock clock;

    AccessTokenExpiryValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Instant expiresAt = token.getExpiresAt();
        if (expiresAt == null || !clock.instant().isBefore(expiresAt)) {
            return OAuth2TokenValidatorResult.failure(EXPIRED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
