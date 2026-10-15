-- 로그아웃: 제출된 Refresh Token이 속한 세션만 한 번에(원자적으로) 폐기한다.
-- 세션 키는 색인에서 찾은 세션 id로 스크립트 안에서 만든다 (Redis 클러스터가 아닌 단일 Redis를 전제로 한다).
-- 돌려주는 값: {'REVOKED'} 세션을 폐기했다 / {'NOT_FOUND'} 알 수 없는 Refresh Token이거나 세션이 이미 없다
local sessionKeyPrefix = ARGV[1]
local refreshTokenKeyPrefix = ARGV[2]

local sessionId = redis.call('GET', KEYS[1])  -- KEYS[1]: 제출된 Refresh Token 해시 → 세션 색인
if not sessionId then
    return {'NOT_FOUND'}
end

local sessionKey = sessionKeyPrefix .. sessionId
local issuedKey = sessionKey .. ':issued'
-- 이 세션에서 발급한 모든 Refresh Token을 더 이상 쓸 수 없게 색인도 함께 지운다.
for _, hash in ipairs(redis.call('SMEMBERS', issuedKey)) do
    redis.call('DEL', refreshTokenKeyPrefix .. hash)
end
redis.call('DEL', KEYS[1], sessionKey, issuedKey, sessionKey .. ':grace')
return {'REVOKED'}
