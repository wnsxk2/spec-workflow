package com.specworkflow.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 로그인 차단 기록. 이메일별 로그인 실패 횟수와 마지막 실패 시각을 Redis에 둔다.
 * 존재하지 않는 이메일도 같은 규칙으로 센다. 이메일은 해시로 바꿔 키로 쓴다.
 */
@Component
class LoginLockout {

    private static final String KEY_PREFIX = "auth:login-failures:";
    private static final RedisScript<Long> BEGIN_ATTEMPT =
            RedisScript.of(new ClassPathResource("auth/begin-login-attempt.lua"), Long.class);

    private final StringRedisTemplate redis;

    LoginLockout(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 비밀번호를 대조하기 전에 로그인 시도를 센다. 로그인 차단 중이면 아무것도 세지 않는다.
     * 그 밖에는 이번 시도를 실패로 미리 센다. 로그인에 성공하면 {@link #clear}로 지운다.
     */
    Attempt begin(String normalizedEmail, Instant now) {
        Long failures = withRedis(() -> redis.execute(
                BEGIN_ATTEMPT,
                List.of(key(normalizedEmail)),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(AuthPolicy.LOGIN_FAILURE_WINDOW.toMillis()),
                String.valueOf(AuthPolicy.LOGIN_LOCKOUT_DURATION.toMillis()),
                String.valueOf(AuthPolicy.LOGIN_FAILURE_LIMIT)));
        return failures == null || failures < 0 ? Attempt.LOCKED_OUT
                : failures >= AuthPolicy.LOGIN_FAILURE_LIMIT ? Attempt.LAST_BEFORE_LOCKOUT
                : Attempt.ALLOWED;
    }

    /** 로그인에 성공하면 그 이메일의 실패 기록을 지운다. */
    void clear(String normalizedEmail) {
        withRedis(() -> redis.delete(key(normalizedEmail)));
    }

    /** 로그인 시도를 센 결과. */
    enum Attempt {
        /** 비밀번호를 대조해도 된다. 틀리면 공통 실패다. */
        ALLOWED,
        /** 비밀번호를 대조해도 되지만, 틀리면 이번 실패로 로그인 차단이 시작된다. */
        LAST_BEFORE_LOCKOUT,
        /** 로그인 차단 중이다. 비밀번호를 대조하지 않는다. */
        LOCKED_OUT
    }

    private static <T> T withRedis(Supplier<T> command) {
        try {
            return command.get();
        } catch (DataAccessException e) {
            throw new AuthUnavailableException(e);
        }
    }

    private static String key(String normalizedEmail) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
