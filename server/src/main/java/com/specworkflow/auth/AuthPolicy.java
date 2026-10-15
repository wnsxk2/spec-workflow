package com.specworkflow.auth;

import java.time.Duration;

/** 로그인 spec이 정한 시간 규칙. 모두 서버의 시계로 계산한다. */
final class AuthPolicy {

    /** Access Token 유효 기간. */
    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);

    /** 마지막 토큰 교환(또는 로그인)으로부터 세션 만료까지의 시간. */
    static final Duration SESSION_IDLE_LIFETIME = Duration.ofMinutes(30);

    private AuthPolicy() {}
}
