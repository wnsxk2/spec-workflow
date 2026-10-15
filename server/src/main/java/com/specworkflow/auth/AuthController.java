package com.specworkflow.auth;

import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AuthController {

    static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    /** Refresh Token 쿠키는 인증 API에만 따라간다. 보호된 요청에는 따라가지 않는다. */
    static final String REFRESH_TOKEN_COOKIE_PATH = "/api/auth";

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/api/auth/login")
    ResponseEntity<AccessTokenResponse> login(@RequestBody LoginRequest request) {
        IssuedTokens result = auth.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken()).toString())
                .body(new AccessTokenResponse(result.accessToken()));
    }

    @PostMapping("/api/auth/refresh")
    ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        IssuedTokens result = auth.rotate(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken()).toString())
                .body(new AccessTokenResponse(result.accessToken()));
    }

    /** 로그아웃은 언제나 성공하고 Refresh Token 쿠키를 지운다. */
    @PostMapping("/api/auth/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie().toString())
                .build();
    }

    @ExceptionHandler(RefreshTokenRejectedException.class)
    ResponseEntity<Map<String, String>> refreshTokenRejected(RefreshTokenRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie().toString())
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String, String>> invalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(LoginLockedException.class)
    ResponseEntity<Map<String, String>> loginLocked(LoginLockedException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AuthUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(AuthUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }

    private static ResponseCookie refreshTokenCookie(String refreshToken) {
        return refreshTokenCookie(refreshToken, AuthPolicy.SESSION_IDLE_LIFETIME);
    }

    /** 브라우저가 Refresh Token 쿠키를 지우게 하는 쿠키. 속성은 설정할 때와 같다. */
    private static ResponseCookie expiredRefreshTokenCookie() {
        return refreshTokenCookie("", Duration.ZERO);
    }

    private static ResponseCookie refreshTokenCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    record LoginRequest(String email, String password) {}

    record AccessTokenResponse(String accessToken) {}
}
