package com.specworkflow.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
     * 제출된 Refresh Token이 세션의 현재 Refresh Token이면 새 Refresh Token으로 교환하고 세션 만료를 다시 센다.
     *
     * @return 교환했으면 세션의 사용자 식별자, 교환할 수 없으면 빈 값
     */
    Optional<UUID> rotate(String submittedHash, String newHash, Instant now) {
        List<?> result = execute(
                ROTATE_REFRESH_TOKEN,
                List.of(REFRESH_TOKEN_KEY_PREFIX + submittedHash),
                submittedHash,
                newHash,
                String.valueOf(now.toEpochMilli()),
                String.valueOf(AuthPolicy.SESSION_IDLE_LIFETIME.toMillis()),
                SESSION_KEY_PREFIX,
                REFRESH_TOKEN_KEY_PREFIX);
        if ("ROTATED".equals(result.get(0))) {
            return Optional.of(UUID.fromString((String) result.get(1)));
        }
        return Optional.empty();
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
