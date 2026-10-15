-- 토큰 교환을 한 번에(원자적으로) 처리한다. 같은 Refresh Token으로 동시에 요청해도 교환은 한 번만 일어나고,
-- 나머지 요청은 유예 시간 규칙으로 같은 새 Refresh Token을 받는다.
-- 세션 키는 색인에서 찾은 세션 id로 스크립트 안에서 만든다 (Redis 클러스터가 아닌 단일 Redis를 전제로 한다).
-- 돌려주는 값:
--   {'ROTATED', 사용자 식별자}             현재 Refresh Token을 새 Refresh Token으로 교환했다
--   {'GRACE', 사용자 식별자, 원래 값}       유예 시간 안의 직전 Refresh Token. 방금 발급한 Refresh Token을 다시 준다
--   {'REUSE_DETECTED', 사용자 식별자}       재사용 감지. 그 세션을 폐기했다
--   {'REJECTED'}                          알 수 없는 Refresh Token이거나 세션 만료
local submittedHash = ARGV[1]       -- 제출된 Refresh Token 해시
local newHash = ARGV[2]             -- 새 Refresh Token 해시
local newRefreshToken = ARGV[3]     -- 새 Refresh Token 원래 값 (유예 시간 동안만 보관)
local now = tonumber(ARGV[4])       -- 서버 시계의 지금 시각(ms)
local sessionLifetime = tonumber(ARGV[5])  -- 세션 만료까지의 시간(ms)
local gracePeriod = tonumber(ARGV[6])      -- 유예 시간(ms)
local sessionKeyPrefix = ARGV[7]
local refreshTokenKeyPrefix = ARGV[8]

local sessionId = redis.call('GET', KEYS[1])  -- KEYS[1]: 제출된 Refresh Token 해시 → 세션 색인
if not sessionId then
    return {'REJECTED'}
end

local sessionKey = sessionKeyPrefix .. sessionId
local issuedKey = sessionKey .. ':issued'
local graceKey = sessionKey .. ':grace'
local session = redis.call('HMGET', sessionKey,
    'userId', 'currentRefreshTokenHash', 'previousRefreshTokenHash', 'lastRotatedAt', 'expiresAt')
local userId, current, previous = session[1], session[2], session[3]
local lastRotatedAt, expiresAt = tonumber(session[4]), tonumber(session[5])
if not userId then
    return {'REJECTED'}
end

-- 세션 만료와 유예 시간은 서버의 시계로 판단한다. Redis의 보관 시간(PX, PEXPIRE)은 지난 기록을 치우는 용도다.
if now >= expiresAt then
    return {'REJECTED'}
end

if submittedHash == current then
    redis.call('HSET', sessionKey,
        'currentRefreshTokenHash', newHash,
        'previousRefreshTokenHash', submittedHash,
        'lastRotatedAt', string.format('%d', now),
        'expiresAt', string.format('%d', now + sessionLifetime))
    redis.call('SET', graceKey, newRefreshToken, 'PX', gracePeriod)
    redis.call('SADD', issuedKey, newHash)
    redis.call('PEXPIRE', sessionKey, sessionLifetime)
    redis.call('PEXPIRE', issuedKey, sessionLifetime)
    -- 세션이 유지되는 동안 이 세션에서 발급한 모든 Refresh Token이 세션을 가리키도록 색인도 함께 연장한다.
    for _, hash in ipairs(redis.call('SMEMBERS', issuedKey)) do
        redis.call('SET', refreshTokenKeyPrefix .. hash, sessionId, 'PX', sessionLifetime)
    end
    return {'ROTATED', userId}
end

if submittedHash == previous and now - lastRotatedAt <= gracePeriod then
    local graceRefreshToken = redis.call('GET', graceKey)
    if graceRefreshToken then
        return {'GRACE', userId, graceRefreshToken}
    end
    -- 서버 시계로는 유예 시간 안이지만 Redis에서 원래 값이 먼저 사라진 경계의 경우다.
    -- 정상적인 재시도일 수 있으므로 세션을 폐기하지 않고 거절만 한다.
    return {'REJECTED'}
end

-- 재사용 감지: 유예 시간이 지난 직전 Refresh Token이거나 두 단계 이전 이상의 Refresh Token이다.
-- 도난 신호로 보고 이 세션만 폐기한다. 이 세션에서 발급한 모든 Refresh Token을 더 이상 쓸 수 없다.
for _, hash in ipairs(redis.call('SMEMBERS', issuedKey)) do
    redis.call('DEL', refreshTokenKeyPrefix .. hash)
end
redis.call('DEL', sessionKey, issuedKey, graceKey)
return {'REUSE_DETECTED', userId}
