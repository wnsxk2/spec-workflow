package com.specworkflow.auth;

/** 로그인이나 토큰 교환으로 발급한 Access Token과 Refresh Token. */
public record IssuedTokens(String accessToken, String refreshToken) {}
