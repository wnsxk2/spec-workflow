package com.specworkflow.auth;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
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
        LoginResult result = auth.login(request.email(), request.password());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(result.refreshToken()).toString())
                .body(new AccessTokenResponse(result.accessToken()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String, String>> invalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AuthUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(AuthUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message", e.getMessage()));
    }

    private static ResponseCookie refreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(AuthPolicy.SESSION_IDLE_LIFETIME)
                .build();
    }

    record LoginRequest(String email, String password) {}

    record AccessTokenResponse(String accessToken) {}
}
