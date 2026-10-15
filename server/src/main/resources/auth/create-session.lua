-- 새 세션을 한 번에(원자적으로) 만든다.
-- KEYS[1] 세션, KEYS[2] 세션에서 발급한 Refresh Token 해시 목록, KEYS[3] Refresh Token 해시 → 세션 색인
-- ARGV[1] 사용자 식별자, ARGV[2] Refresh Token 해시, ARGV[3] 마지막 토큰 교환(또는 로그인) 시각(ms),
-- ARGV[4] 세션 만료 시각(ms), ARGV[5] 세션 id, ARGV[6] Redis 보관 시간(ms)
redis.call('HSET', KEYS[1],
    'userId', ARGV[1],
    'currentRefreshTokenHash', ARGV[2],
    'lastRotatedAt', ARGV[3],
    'expiresAt', ARGV[4])
redis.call('PEXPIRE', KEYS[1], ARGV[6])
redis.call('SADD', KEYS[2], ARGV[2])
redis.call('PEXPIRE', KEYS[2], ARGV[6])
redis.call('SET', KEYS[3], ARGV[5], 'PX', ARGV[6])
return {"CREATED"}
