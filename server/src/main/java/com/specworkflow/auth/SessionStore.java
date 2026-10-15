package com.specworkflow.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** 세션을 Redis에 기록한다 (ADR-0001). 서버가 기억하는 것은 세션뿐이다. */
@Component
class SessionStore {

    private static final RedisScript<Long> CREATE_SESSION =
            RedisScript.of(new ClassPathResource("auth/create-session.lua"), Long.class);

    private final StringRedisTemplate redis;

    SessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 로그인할 때마다 새 세션을 만든다. 같은 사용자의 다른 세션은 건드리지 않는다. */
    void create(UUID userId, String refreshTokenHash, Instant now) {
        String sessionId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(AuthPolicy.SESSION_IDLE_LIFETIME);
        try {
            redis.execute(
                    CREATE_SESSION,
                    List.of(sessionKey(sessionId), issuedKey(sessionId), refreshTokenKey(refreshTokenHash)),
                    userId.toString(),
                    refreshTokenHash,
                    String.valueOf(now.toEpochMilli()),
                    String.valueOf(expiresAt.toEpochMilli()),
                    sessionId,
                    String.valueOf(AuthPolicy.SESSION_IDLE_LIFETIME.toMillis()));
        } catch (DataAccessException e) {
            throw new AuthUnavailableException(e);
        }
    }

    private static String sessionKey(String sessionId) {
        return "auth:session:" + sessionId;
    }

    private static String issuedKey(String sessionId) {
        return "auth:session:" + sessionId + ":issued";
    }

    private static String refreshTokenKey(String refreshTokenHash) {
        return "auth:refresh-token:" + refreshTokenHash;
    }
}
