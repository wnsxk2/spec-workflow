package com.specworkflow.auth;

/** 같은 이메일로 로그인 실패가 이어져 그 이메일의 로그인을 잠시 막고 있다. */
public class LoginLockedException extends RuntimeException {

    LoginLockedException() {
        super("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요");
    }
}
