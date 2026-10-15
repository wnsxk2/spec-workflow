package com.specworkflow.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 세션을 Redis에 기록한다 (ADR-0001). 서버가 기억하는 것은 세션뿐이다.
 * 세션에는 사용자 식별자, 현재·직전 Refresh Token의 해시, 마지막 토큰 교환 시각, 세션 만료 시각,
 * 그리고 이 세션에서 발급한 Refresh Token의 해시 목록이 연결된다.
 */
@Component
class SessionStore {

    private static final String SESSION_KEY_PREFIX = "auth:session:";
    private static final String REFRESH_TOKEN_KEY_PREFIX = "auth:refresh-token:";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CREATE_SESSION =
            RedisScript.of(new ClassPathResource("auth/create-session.lua"), List.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ROTATE_REFRESH_TOKEN =
            RedisScript.of(new ClassPathResource("auth/rotate-refresh-token.lua"), List.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> REVOKE_SESSION =
            RedisScript.of(new ClassPathResource("auth/revoke-session.lua"), List.class);

    private final StringRedisTemplate redis;

    SessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 로그인할 때마다 새 세션을 만든다. 같은 사용자의 다른 세션은 건드리지 않는다. */
    void create(UUID userId, String refreshTokenHash, Instant now) {
        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(AuthPolicy.SESSION_IDLE_LIFETIME);
        execute(
                CREATE_SESSION,
                List.of(SESSION_KEY_PREFIX + sessionId, SESSION_KEY_PREFIX + sessionId + ":issued",
                        REFRESH_TOKEN_KEY_PREFIX + refreshTokenHash),
                userId.toString(),
                refreshTokenHash,
                String.valueOf(now.toEpochMilli()),
                String.valueOf(expiresAt.toEpochMilli()),
                sessionId,
                String.valueOf(AuthPolicy.SESSION_IDLE_LIFETIME.toMillis()));
    }

    /**
     * 토큰 교환 규칙에 따라 제출된 Refresh Token을 처리한다.
     * 현재 Refresh Token이면 새 Refresh Token으로 교환하고 세션 만료를 다시 센다.
     * 유예 시간 안의 직전 Refresh Token이면 방금 발급한 Refresh Token을 다시 준다.
     * 그 밖에 이 세션에서 발급했던 Refresh Token이면 재사용 감지로 세션을 폐기한다.
     */
    Rotation rotate(String submittedRefreshToken, String newRefreshToken, Instant now) {
        String submittedHash = RefreshTokens.hash(submittedRefreshToken);
        List<?> result = execute(
                ROTATE_REFRESH_TOKEN,
                List.of(REFRESH_TOKEN_KEY_PREFIX + submittedHash),
                submittedHash,
                RefreshTokens.hash(newRefreshToken),
                newRefreshToken,
                String.valueOf(now.toEpochMilli()),
                String.valueOf(AuthPolicy.SESSION_IDLE_LIFETIME.toMillis()),
                String.valueOf(AuthPolicy.GRACE_PERIOD.toMillis()),
                SESSION_KEY_PREFIX,
                REFRESH_TOKEN_KEY_PREFIX);
        return switch ((String) result.get(0)) {
            case "ROTATED" -> new Rotation.Issued(UUID.fromString((String) result.get(1)), newRefreshToken);
            case "GRACE" -> new Rotation.Issued(UUID.fromString((String) result.get(1)), (String) result.get(2));
            case "REUSE_DETECTED" -> new Rotation.ReuseDetected(UUID.fromString((String) result.get(1)));
            default -> new Rotation.Rejected();
        };
    }

    /** 토큰 교환의 결과. */
    sealed interface Rotation {

        /**
         * 돌려줄 Refresh Token이 있다. 현재 Refresh Token을 교환했으면 새 Refresh Token이고,
         * 유예 시간 안의 직전 Refresh Token이면 방금 발급한 Refresh Token이다.
         */
        record Issued(UUID userId, String refreshToken) implements Rotation {}

        /** 재사용 감지. 도난 신호로 보고 그 세션을 폐기했다. */
        record ReuseDetected(UUID userId) implements Rotation {}

        /** 알 수 없는 Refresh Token이거나 세션 만료 등으로 교환할 수 없다. */
        record Rejected() implements Rotation {}
    }

    /** 로그아웃: 제출된 Refresh Token이 속한 세션만 폐기한다. 그런 세션이 없으면 아무 일도 하지 않는다. */
    void revoke(String refreshToken) {
        execute(
                REVOKE_SESSION,
                List.of(REFRESH_TOKEN_KEY_PREFIX + RefreshTokens.hash(refreshToken)),
                SESSION_KEY_PREFIX,
                REFRESH_TOKEN_KEY_PREFIX);
    }

    @SuppressWarnings("rawtypes")
    private List<?> execute(RedisScript<List> script, List<String> keys, String... args) {
        try {
            return redis.execute(script, keys, (Object[]) args);
        } catch (DataAccessException e) {
            throw new AuthUnavailableException(e);
        }
    }
}
