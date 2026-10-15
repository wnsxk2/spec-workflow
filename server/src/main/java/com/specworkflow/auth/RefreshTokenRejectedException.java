package com.specworkflow.auth;

/** Refresh Token이 없거나, 알 수 없거나, 세션 만료 등으로 토큰 교환에 쓸 수 없다. */
public class RefreshTokenRejectedException extends RuntimeException {

    RefreshTokenRejectedException() {
        super("다시 로그인해 주세요");
    }
}
