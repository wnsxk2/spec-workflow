package com.specworkflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.specworkflow.support.HttpApi;
import com.specworkflow.support.HttpApiTest;
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
@DisplayName("로그아웃")
class LogoutHttpApiTest {

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
    @DisplayName("로그아웃하면 그 세션을 폐기하고 Refresh Token 쿠키를 지워, 그 Refresh Token으로는 토큰 교환할 수 없다")
    void logoutRevokesCurrentSession() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();

        HttpApi.Response response = api.logout(refreshToken);

        response.assertLogoutSucceeded();
        api.rotate(refreshToken).assertRefreshTokenRejected();
    }

    @Test
    @DisplayName("로그아웃은 같은 사용자가 다른 기기에서 만든 세션을 폐기하지 않는다")
    void logoutKeepsOtherSessions() {
        TestUser user = users.create("correct-password");
        String laptop = api.login(user.email(), user.password()).refreshToken();
        String phone = api.login(user.email(), user.password()).refreshToken();

        api.logout(laptop);

        assertThat(api.rotate(phone).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없어도 로그아웃은 성공하고 Refresh Token 쿠키를 지운다")
    void logoutWithoutRefreshTokenSucceeds() {
        api.logout(null).assertLogoutSucceeded();
    }

    @Test
    @DisplayName("알 수 없는 Refresh Token으로도 로그아웃은 성공하고 Refresh Token 쿠키를 지운다")
    void logoutWithUnknownRefreshTokenSucceeds() {
        api.logout("unknown-refresh-token").assertLogoutSucceeded();
    }

    @Test
    @DisplayName("세션 만료 뒤에도 로그아웃은 성공하고 Refresh Token 쿠키를 지운다")
    void logoutAfterSessionExpirySucceeds() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();

        clock.advance(Duration.ofMinutes(30));

        api.logout(refreshToken).assertLogoutSucceeded();
    }

    @Test
    @DisplayName("같은 Refresh Token으로 로그아웃을 반복해도 매번 성공한다")
    void repeatedLogoutSucceeds() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();

        api.logout(refreshToken).assertLogoutSucceeded();
        api.logout(refreshToken).assertLogoutSucceeded();
    }

    @Test
    @DisplayName("로그아웃 전에 발급한 Access Token은 로그아웃 뒤에도 발급 15분 뒤까지 유효하다")
    void issuedAccessTokenStaysValidUntilItExpires() {
        TestUser user = users.create("correct-password");
        HttpApi.Response login = api.login(user.email(), user.password());

        api.logout(login.refreshToken());

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(api.get("/api/me", login.accessToken()).status()).isEqualTo(200);
        clock.advance(Duration.ofSeconds(1));
        assertThat(api.get("/api/me", login.accessToken()).status()).isEqualTo(401);
    }
}
