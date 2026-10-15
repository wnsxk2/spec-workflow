package com.specworkflow.auth;

/** 이메일이 없거나 비밀번호가 틀렸다. 둘을 구분하지 않는다. */
public class InvalidCredentialsException extends RuntimeException {

    InvalidCredentialsException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다");
    }
}
