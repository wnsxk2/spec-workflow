package com.specworkflow.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Access Token을 발급한다. 담는 내용은 사용자 식별자, 발급 시각, 만료 시각뿐이다. */
@Component
class AccessTokens {

    private final JwtEncoder encoder;

    AccessTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    String issue(UUID userId, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(AuthPolicy.ACCESS_TOKEN_LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
