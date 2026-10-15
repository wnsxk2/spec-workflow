package com.specworkflow.auth;

/** 세션 저장소(Redis)를 쓸 수 없어 지금은 처리할 수 없다. */
public class AuthUnavailableException extends RuntimeException {

    AuthUnavailableException(Throwable cause) {
        super("잠시 후 다시 시도해 주세요", cause);
    }
}
