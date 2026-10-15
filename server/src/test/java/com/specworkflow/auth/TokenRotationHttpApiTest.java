package com.specworkflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.specworkflow.support.HttpApi;
import com.specworkflow.support.HttpApiTest;
import com.specworkflow.support.SetCookie;
import com.specworkflow.support.TestClock;
import com.specworkflow.support.TestUsers;
import com.specworkflow.support.TestUsers.TestUser;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

@HttpApiTest
@DisplayName("토큰 교환")
class TokenRotationHttpApiTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TestClock clock;

    HttpApi api;
    TestUsers users;

    @BeforeEach
    void setUp() {
        api = new HttpApi(port);
        users = new TestUsers(jdbc);
        clock.reset();
    }

    @Test
    @DisplayName("현재 Refresh Token으로 토큰 교환하면 200과 새 Access Token, 새 Refresh Token 쿠키를 받는다")
    void rotateCurrentRefreshToken() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();

        HttpApi.Response response = api.rotate(refreshToken);

        assertThat(response.status()).isEqualTo(200);
        SetCookie cookie = response.cookie("refresh_token").orElseThrow();
        assertThat(cookie.value()).isNotBlank().isNotEqualTo(refreshToken).doesNotContain(".");
        assertThat(cookie.has("HttpOnly")).isTrue();
        assertThat(cookie.has("Secure")).isTrue();
        assertThat(cookie.attribute("SameSite")).hasValue("Strict");
        assertThat(cookie.attribute("Path")).hasValue("/api/auth");
        HttpApi.Response me = api.get("/api/me", response.accessToken());
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().get("userId").asString()).isEqualTo(user.id().toString());
    }
    @Test
    @DisplayName("토큰 교환에 쓴 Refresh Token은 직전 Refresh Token이 되어 더 이상 현재 Refresh Token으로 교환되지 않는다")
    void usedRefreshTokenIsNoLongerCurrent() {
        TestUser user = users.create("correct-password");
        String first = api.login(user.email(), user.password()).refreshToken();
        assertThat(api.rotate(first).status()).isEqualTo(200);

        // 유예 시간(10초)이 지난 뒤에 확인한다.
        clock.advance(Duration.ofSeconds(10).plusSeconds(1));

        assertThat(api.rotate(first).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 401과 함께 Refresh Token 쿠키를 지운다")
    void missingRefreshTokenIsRejected() {
        api.rotate(null).assertRefreshTokenRejected();
    }

    @Test
    @DisplayName("알 수 없는 Refresh Token은 401과 함께 Refresh Token 쿠키를 지운다")
    void unknownRefreshTokenIsRejected() {
        api.rotate("unknown-refresh-token").assertRefreshTokenRejected();
    }

    @Test
    @DisplayName("로그인 후 30분이 되기 전에는 토큰 교환할 수 있다")
    void rotateBeforeSessionExpiry() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();

        clock.advance(Duration.ofMinutes(30).minusSeconds(1));

        assertThat(api.rotate(refreshToken).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("로그인 후 30분이 지나면 세션 만료로 401과 함께 Refresh Token 쿠키를 지운다")
    void sessionExpiresThirtyMinutesAfterLogin() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();

        clock.advance(Duration.ofMinutes(30));

        api.rotate(refreshToken).assertRefreshTokenRejected();
    }

    @Test
    @DisplayName("토큰 교환하면 세션 만료를 그 시점부터 30분으로 다시 세고, 마지막 토큰 교환 후 30분이 지나면 세션 만료다")
    void rotationExtendsSessionExpiry() {
        TestUser user = users.create("correct-password");
        String first = api.login(user.email(), user.password()).refreshToken();

        clock.advance(Duration.ofMinutes(20));
        String second = api.rotate(first).refreshToken();

        // 로그인 기준으로는 49분 59초가 지났지만, 마지막 토큰 교환 기준으로는 29분 59초다.
        clock.advance(Duration.ofMinutes(30).minusSeconds(1));
        HttpApi.Response beforeExpiry = api.rotate(second);
        assertThat(beforeExpiry.status()).isEqualTo(200);

        clock.advance(Duration.ofMinutes(30));
        api.rotate(beforeExpiry.refreshToken()).assertRefreshTokenRejected();
    }

    @Test
    @DisplayName("토큰 교환을 계속하는 세션에는 최대 수명 상한이 없다")
    void sessionHasNoMaximumLifetime() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();

        // 세션 만료(30분)가 오기 직전마다 토큰 교환하며, 상한이 있을 법한 시간(며칠)을 훨씬 넘겨 계속한다.
        Duration justBeforeExpiry = Duration.ofMinutes(30).minusSeconds(1);
        for (int i = 0; i < 200; i++) {
            clock.advance(justBeforeExpiry);
            HttpApi.Response response = api.rotate(refreshToken);
            assertThat(response.status()).isEqualTo(200);
            refreshToken = response.refreshToken();
        }
    }

    @Test
    @DisplayName("토큰 교환으로 받은 Access Token도 발급 15분 뒤부터 거절한다")
    void rotatedAccessTokenExpiresAfter15Minutes() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();
        clock.advance(Duration.ofMinutes(10));
        String accessToken = api.rotate(refreshToken).accessToken();

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(api.get("/api/me", accessToken).status()).isEqualTo(200);

        clock.advance(Duration.ofSeconds(1));
        assertThat(api.get("/api/me", accessToken).status()).isEqualTo(401);
    }

}
