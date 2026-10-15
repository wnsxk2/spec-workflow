-- 토큰 교환을 한 번에(원자적으로) 처리한다. 같은 Refresh Token으로 동시에 요청해도 교환은 한 번만 일어난다.
-- 세션 키는 색인에서 찾은 세션 id로 스크립트 안에서 만든다 (Redis 클러스터가 아닌 단일 Redis를 전제로 한다).
-- KEYS[1] 제출된 Refresh Token 해시 → 세션 색인
-- ARGV[1] 제출된 Refresh Token 해시, ARGV[2] 새 Refresh Token 해시, ARGV[3] 지금 시각(ms),
-- ARGV[4] 세션 만료까지의 시간(ms), ARGV[5] 세션 키 접두사, ARGV[6] 색인 키 접두사
-- 돌려주는 값: {'ROTATED', 사용자 식별자} 또는 {'REJECTED'}
local sessionId = redis.call('GET', KEYS[1])
if not sessionId then
    return {'REJECTED'}
end

local sessionKey = ARGV[5] .. sessionId
local issuedKey = sessionKey .. ':issued'
local session = redis.call('HMGET', sessionKey, 'userId', 'currentRefreshTokenHash', 'expiresAt')
local userId, current, expiresAt = session[1], session[2], session[3]
if not userId or current ~= ARGV[1] then
    return {'REJECTED'}
end

-- 세션 만료는 서버의 시계로 판단한다. Redis의 보관 시간(PEXPIRE)은 지난 기록을 치우는 용도다.
local now = tonumber(ARGV[3])
if now >= tonumber(expiresAt) then
    return {'REJECTED'}
end

local lifetime = tonumber(ARGV[4])
redis.call('HSET', sessionKey,
    'currentRefreshTokenHash', ARGV[2],
    'previousRefreshTokenHash', ARGV[1],
    'lastRotatedAt', ARGV[3],
    'expiresAt', string.format('%d', now + lifetime))
redis.call('SADD', issuedKey, ARGV[2])
redis.call('PEXPIRE', sessionKey, lifetime)
redis.call('PEXPIRE', issuedKey, lifetime)
-- 세션이 유지되는 동안 이 세션에서 발급한 모든 Refresh Token이 세션을 가리키도록 색인도 함께 연장한다.
for _, hash in ipairs(redis.call('SMEMBERS', issuedKey)) do
    redis.call('SET', ARGV[6] .. hash, sessionId, 'PX', lifetime)
end
return {'ROTATED', userId}
