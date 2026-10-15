-- 로그인 시도를 비밀번호 대조 전에 한 번에(원자적으로) 센다.
-- 로그인 차단 중이면 아무것도 바꾸지 않고 -1을 돌려준다 (차단 기간이 늘어나지 않는다).
-- 아니면 이번 시도를 실패로 미리 세고, 지금까지 이어진 실패 횟수(이번 시도 포함)를 돌려준다.
-- 로그인에 성공하면 호출하는 쪽이 이 기록을 지운다. 그래서 동시에 들어온 시도가 함께 비밀번호를 대조받지 못한다.
-- 시간은 서버의 시계로 판단한다. Redis의 보관 시간(PEXPIRE)은 지난 기록을 치우는 용도다.
-- KEYS[1] 이메일별 로그인 실패 기록
local now = tonumber(ARGV[1])              -- 서버 시계의 지금 시각(ms)
local failureWindow = tonumber(ARGV[2])    -- 마지막 실패로부터 실패 횟수가 사라질 때까지의 시간(ms)
local lockoutDuration = tonumber(ARGV[3])  -- 로그인 차단 기간(ms)
local failureLimit = tonumber(ARGV[4])     -- 이 횟수의 실패로 로그인 차단이 시작된다

local record = redis.call('HMGET', KEYS[1], 'failures', 'lastFailedAt')
local failures = tonumber(record[1]) or 0
local lastFailedAt = tonumber(record[2]) or 0

if failures >= failureLimit and now - lastFailedAt < lockoutDuration then
    return -1
end
if failures > 0 and now - lastFailedAt >= failureWindow then
    failures = 0
end

failures = failures + 1
redis.call('HSET', KEYS[1], 'failures', failures, 'lastFailedAt', string.format('%d', now))
redis.call('PEXPIRE', KEYS[1], math.max(failureWindow, lockoutDuration))
return failures
