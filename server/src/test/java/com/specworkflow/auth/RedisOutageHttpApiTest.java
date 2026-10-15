package com.specworkflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.specworkflow.support.HttpApi;
import com.specworkflow.support.HttpApiTest;
import com.specworkflow.support.TestUsers;
import com.specworkflow.support.TestUsers.TestUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;

@HttpApiTest
@DisplayName("Redis 장애")
class RedisOutageHttpApiTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcClient jdbc;

    /** 테스트용 Redis 컨테이너. 일시 정지해 Redis가 멈춘 상황을 만든다. */
    @Autowired
    GenericContainer<?> redis;

    HttpApi api;
    TestUsers users;

    @BeforeEach
    void setUp() {
        api = new HttpApi(port);
        users = new TestUsers(jdbc);
    }

    @AfterEach
    void resumeRedis() {
        if (Boolean.TRUE.equals(redis.getCurrentContainerInfo().getState().getPaused())) {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }

    @Test
    @DisplayName("Redis가 멈추면 로그인은 503과 잠시 후 다시 시도 안내로 실패한다")
    void loginFailsWith503() {
        TestUser user = users.create("correct-password");

        pauseRedis();
        HttpApi.Response response = api.login(user.email(), user.password());

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.json().get("message").asString()).isEqualTo("잠시 후 다시 시도해 주세요");
        assertThat(response.cookie("refresh_token")).isEmpty();
    }

    @Test
    @DisplayName("Redis가 멈추면 토큰 교환은 503과 잠시 후 다시 시도 안내로 실패한다")
    void tokenRotationFailsWith503() {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();

        pauseRedis();
        HttpApi.Response response = api.rotate(refreshToken);

        assertThat(response.status()).isEqualTo(503);
        assertThat(response.json().get("message").asString()).isEqualTo("잠시 후 다시 시도해 주세요");
        assertThat(response.cookie("refresh_token")).isEmpty();
    }

    @Test
    @DisplayName("Redis가 멈춰도 유효한 Access Token의 보호된 요청은 성공한다")
    void protectedRequestDoesNotNeedRedis() {
        TestUser user = users.create("correct-password");
        String accessToken = api.login(user.email(), user.password()).accessToken();

        pauseRedis();

        HttpApi.Response response = api.get("/api/me", accessToken);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("userId").asString()).isEqualTo(user.id().toString());
    }

    private void pauseRedis() {
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
    }
}
