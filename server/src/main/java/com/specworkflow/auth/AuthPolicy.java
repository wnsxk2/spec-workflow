package com.specworkflow.auth;

import java.time.Duration;

/** 로그인 spec이 정한 시간 규칙. 모두 서버의 시계로 계산한다. */
final class AuthPolicy {

    /** Access Token 유효 기간. */
    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);

    /** 마지막 토큰 교환(또는 로그인)으로부터 세션 만료까지의 시간. */
    static final Duration SESSION_IDLE_LIFETIME = Duration.ofMinutes(30);

    /** 토큰 교환 직후, 방금 쓴 Refresh Token이 다시 제출되어도 재사용 감지로 보지 않는 시간. */
    static final Duration GRACE_PERIOD = Duration.ofSeconds(10);

    /** 이 횟수만큼 로그인 실패가 이어지면 로그인 차단이 시작된다. */
    static final int LOGIN_FAILURE_LIMIT = 5;

    /** 마지막 로그인 실패로부터 이 시간이 지나면 실패 횟수가 사라진다. */
    static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);

    /** 로그인 차단 기간. 차단을 일으킨 실패 시각부터 센다. */
    static final Duration LOGIN_LOCKOUT_DURATION = Duration.ofMinutes(15);

    private AuthPolicy() {}
}
