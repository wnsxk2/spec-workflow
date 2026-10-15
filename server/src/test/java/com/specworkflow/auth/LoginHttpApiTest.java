package com.specworkflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.specworkflow.support.HttpApi;
import com.specworkflow.support.HttpApiTest;
import com.specworkflow.support.SetCookie;
import com.specworkflow.support.TestUsers;
import com.specworkflow.support.TestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

@HttpApiTest
@DisplayName("로그인")
class LoginHttpApiTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    HttpApi api;
    TestUsers users;

    @BeforeEach
    void setUp() {
        api = new HttpApi(port);
        users = new TestUsers(jdbc);
    }

    @Test
    @DisplayName("올바른 이메일과 비밀번호로 로그인하면 200과 Access Token, Refresh Token 쿠키를 받는다")
    void loginSucceeds() {
        TestUser user = users.create("correct-password");

        HttpApi.Response response = api.login(user.email(), user.password());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.cookie("refresh_token")).hasValueSatisfying(
                cookie -> assertThat(cookie.value()).isNotBlank());
    }
    @Test
    @DisplayName("Refresh Token 쿠키는 HttpOnly, Secure, SameSite=Strict이고 경로가 /api/auth로 제한된다")
    void refreshTokenCookieAttributes() {
        TestUser user = users.create("correct-password");

        SetCookie cookie = api.login(user.email(), user.password()).cookie("refresh_token").orElseThrow();

        assertThat(cookie.has("HttpOnly")).isTrue();
        assertThat(cookie.has("Secure")).isTrue();
        assertThat(cookie.attribute("SameSite")).hasValue("Strict");
        assertThat(cookie.attribute("Path")).hasValue("/api/auth");
    }

    @Test
    @DisplayName("Refresh Token은 JWT가 아닌, 추측할 수 없는 긴 무작위 문자열이다")
    void refreshTokenIsLongRandomString() {
        TestUser user = users.create("correct-password");

        String first = api.login(user.email(), user.password()).cookie("refresh_token").orElseThrow().value();
        String second = api.login(user.email(), user.password()).cookie("refresh_token").orElseThrow().value();

        // JWT는 점(.)으로 나뉜 세 부분으로 이루어진다.
        assertThat(first).doesNotContain(".");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("없는 이메일과 틀린 비밀번호는 같은 401 공통 메시지를 받는다")
    void loginFailureMessageIsTheSame() {
        TestUser user = users.create("correct-password");

        HttpApi.Response wrongPassword = api.login(user.email(), "wrong-password");
        HttpApi.Response unknownEmail = api.login("nobody-" + user.email(), "correct-password");

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownEmail.status()).isEqualTo(401);
        assertThat(wrongPassword.json().get("message").asString()).isEqualTo("이메일 또는 비밀번호가 올바르지 않습니다");
        assertThat(unknownEmail.raw().body()).isEqualTo(wrongPassword.raw().body());
        assertThat(wrongPassword.cookie("refresh_token")).isEmpty();
        assertThat(unknownEmail.cookie("refresh_token")).isEmpty();
    }

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 바꿔 비교한다")
    void loginNormalizesEmail() {
        TestUser user = users.create("correct-password");

        HttpApi.Response response = api.login("  " + user.email().toUpperCase() + " ", user.password());

        assertThat(response.status()).isEqualTo(200);
    }
    @Test
    @DisplayName("로그인할 때마다 새 세션을 만들어, 같은 사용자가 여러 기기에서 동시에 로그인할 수 있다")
    void eachLoginStartsANewSession() {
        TestUser user = users.create("correct-password");

        HttpApi.Response phone = api.login(user.email(), user.password());
        HttpApi.Response laptop = api.login(user.email(), user.password());

        assertThat(laptop.cookie("refresh_token").orElseThrow().value())
                .isNotEqualTo(phone.cookie("refresh_token").orElseThrow().value());
        assertThat(api.get("/api/me", phone.accessToken()).status()).isEqualTo(200);
        assertThat(api.get("/api/me", laptop.accessToken()).status()).isEqualTo(200);
    }
}
