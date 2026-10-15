package com.specworkflow.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 인증 모듈. 세션 저장소와 Access Token 발급을 안에 숨긴다.
 * 토큰 교환(02)과 로그아웃(05)은 이후 티켓에서 더한다.
 */
@Service
public class AuthService {

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
    public LoginResult login(String email, String password) {
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
        return new LoginResult(accessTokens.issue(userId, now), refreshToken);
    }

    /** 이메일은 앞뒤 공백을 지우고 소문자로 바꾼 뒤 비교한다. */
    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
