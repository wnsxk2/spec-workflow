package com.specworkflow.auth;

/** 로그인에 성공해 받은 Access Token과 Refresh Token. */
public record LoginResult(String accessToken, String refreshToken) {}
