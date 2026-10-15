package com.specworkflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 인증 모듈. 세션 저장소와 Access Token 발급을 안에 숨긴다.
 * 로그아웃(05)은 이후 티켓에서 더한다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserCredentials credentials;
    private final SessionStore sessions;
    private final AccessTokens accessTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    /** 이메일이 없을 때도 비밀번호 대조를 한 번 해서, 응답 시간으로 가입 여부를 알 수 없게 한다. */
    private final String dummyPasswordHash;

    AuthService(
            UserCredentials credentials,
            SessionStore sessions,
            AccessTokens accessTokens,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.credentials = credentials;
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 이메일과 비밀번호를 확인하고 새 세션을 시작한다.
     *
     * @throws InvalidCredentialsException 이메일이 없거나 비밀번호가 틀렸을 때
     * @throws AuthUnavailableException 세션을 기록할 수 없을 때
     */
    public IssuedTokens login(String email, String password) {
        Optional<UserCredentials.StoredCredential> stored = credentials.findByEmail(normalize(email));
        String candidate = password == null ? "" : password;
        boolean matches = passwordEncoder.matches(candidate, stored.map(UserCredentials.StoredCredential::passwordHash)
                .orElse(dummyPasswordHash));
        if (stored.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }

        UUID userId = stored.get().userId();
        Instant now = clock.instant();
        String refreshToken = RefreshTokens.generate();
        sessions.create(userId, RefreshTokens.hash(refreshToken), now);
        return new IssuedTokens(accessTokens.issue(userId, now), refreshToken);
    }

    /**
     * 토큰 교환. 현재 Refresh Token을 내면 새 Access Token과 새 Refresh Token을 발급하고 세션 만료를 다시 센다.
     * 유예 시간 안의 직전 Refresh Token에는 방금 발급한 Refresh Token을 다시 주고, 그 밖에 이미 쓴 Refresh Token이면
     * 재사용 감지로 그 세션을 폐기한다.
     *
     * @throws RefreshTokenRejectedException Refresh Token이 없거나 교환할 수 없을 때
     * @throws AuthUnavailableException 세션 저장소를 쓸 수 없을 때
     */
    public IssuedTokens rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new RefreshTokenRejectedException();
        }
        Instant now = clock.instant();
        return switch (sessions.rotate(refreshToken, RefreshTokens.generate(), now)) {
            case SessionStore.Rotation.Issued issued ->
                    new IssuedTokens(accessTokens.issue(issued.userId(), now), issued.refreshToken());
            case SessionStore.Rotation.ReuseDetected reuse -> {
                log.warn("재사용 감지: 이미 토큰 교환에 쓴 Refresh Token이 다시 제출되어 세션을 폐기했습니다. userId={}",
                        reuse.userId());
                throw new RefreshTokenRejectedException();
            }
            case SessionStore.Rotation.Rejected rejected -> throw new RefreshTokenRejectedException();
        };
    }

    /** 이메일은 앞뒤 공백을 지우고 소문자로 바꾼 뒤 비교한다. */
    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
