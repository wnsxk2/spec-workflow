package com.specworkflow.auth;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import com.specworkflow.support.HttpApi;
import com.specworkflow.support.HttpApiTest;
import com.specworkflow.support.TestClock;
import com.specworkflow.support.TestUsers;
import com.specworkflow.support.TestUsers.TestUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;

@HttpApiTest
@DisplayName("유예 시간과 재사용 감지")
class GracePeriodAndReuseDetectionHttpApiTest {

    private static final Duration GRACE_PERIOD = Duration.ofSeconds(10);

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
    @DisplayName("유예 시간 안에 다시 제출된 직전 Refresh Token에는 방금 발급한 것과 같은 Refresh Token과 새 Access Token을 돌려준다")
    void previousRefreshTokenWithinGracePeriodGetsTheSameRefreshToken() {
        TestUser user = users.create("correct-password");
        String previous = api.login(user.email(), user.password()).refreshToken();
        HttpApi.Response rotated = api.rotate(previous);

        clock.advance(GRACE_PERIOD);
        HttpApi.Response retried = api.rotate(previous);

        assertThat(retried.status()).isEqualTo(200);
        assertThat(retried.refreshToken()).isEqualTo(rotated.refreshToken());
        assertThat(retried.accessToken()).isNotEqualTo(rotated.accessToken());
        assertThat(api.get("/api/me", retried.accessToken()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("직전 Refresh Token을 반복 제출해도 새 Refresh Token을 더 만들지 않고, 유예 시간은 처음 토큰 교환한 시각부터 센다")
    void repeatedSubmissionsDoNotRestartGracePeriod() {
        TestUser user = users.create("correct-password");
        String previous = api.login(user.email(), user.password()).refreshToken();
        String rotated = api.rotate(previous).refreshToken();

        clock.advance(Duration.ofSeconds(5));
        assertThat(api.rotate(previous).refreshToken()).isEqualTo(rotated);
        clock.advance(Duration.ofSeconds(5));
        assertThat(api.rotate(previous).refreshToken()).isEqualTo(rotated);

        // 처음 토큰 교환한 시각으로부터 10초가 지났다. 반복 제출한 시각부터 셌다면 아직 5초다.
        clock.advance(Duration.ofMillis(1));
        HttpApi.Response afterGracePeriod = api.rotate(previous);

        afterGracePeriod.assertRefreshTokenRejected();
    }

    @Test
    @DisplayName("유예 시간이 지난 직전 Refresh Token은 재사용 감지로 세션을 폐기해, 그 세션의 최신 Refresh Token도 쓸 수 없다")
    void previousRefreshTokenAfterGracePeriodRevokesSession() {
        TestUser user = users.create("correct-password");
        String previous = api.login(user.email(), user.password()).refreshToken();
        String latest = api.rotate(previous).refreshToken();

        clock.advance(GRACE_PERIOD.plusMillis(1));
        api.rotate(previous).assertRefreshTokenRejected();

        api.rotate(latest).assertRefreshTokenRejected();
    }

    @Test
    @DisplayName("두 단계 이전의 Refresh Token은 유예 시간 안이라도 재사용 감지로 세션을 폐기한다")
    void olderRefreshTokenRevokesSessionEvenWithinGracePeriod() {
        TestUser user = users.create("correct-password");
        String twoStepsOld = api.login(user.email(), user.password()).refreshToken();
        String previous = api.rotate(twoStepsOld).refreshToken();
        String latest = api.rotate(previous).refreshToken();

        api.rotate(twoStepsOld).assertRefreshTokenRejected();

        api.rotate(latest).assertRefreshTokenRejected();
    }

    @Test
    @DisplayName("재사용 감지는 그 세션만 폐기하고, 같은 사용자의 다른 세션은 계속 쓸 수 있다")
    void reuseDetectionRevokesOnlyThatSession() {
        TestUser user = users.create("correct-password");
        HttpApi.Response stolenLogin = api.login(user.email(), user.password());
        String otherSession = api.login(user.email(), user.password()).refreshToken();
        String stolen = stolenLogin.refreshToken();
        api.rotate(stolen);

        clock.advance(GRACE_PERIOD.plusMillis(1));
        api.rotate(stolen).assertRefreshTokenRejected();

        HttpApi.Response other = api.rotate(otherSession);
        assertThat(other.status()).isEqualTo(200);
        assertThat(api.get("/api/me", other.accessToken()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("세션 폐기 뒤에도 이미 발급된 Access Token은 자신의 만료 시각까지 유효하다")
    void issuedAccessTokenStaysValidAfterRevocation() {
        TestUser user = users.create("correct-password");
        HttpApi.Response login = api.login(user.email(), user.password());
        api.rotate(login.refreshToken());

        clock.advance(GRACE_PERIOD.plusMillis(1));
        api.rotate(login.refreshToken()).assertRefreshTokenRejected();

        assertThat(api.get("/api/me", login.accessToken()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("같은 Refresh Token으로 동시에 토큰 교환해도 새 Refresh Token은 하나만 만들어지고, 그 토큰으로 다음 토큰 교환할 수 있다")
    void concurrentRotationsShareOneNewRefreshToken() throws Exception {
        TestUser user = users.create("correct-password");
        String refreshToken = api.login(user.email(), user.password()).refreshToken();
        int tabs = 10;
        CountDownLatch start = new CountDownLatch(1);

        List<HttpApi.Response> responses;
        try (ExecutorService executor = Executors.newFixedThreadPool(tabs)) {
            List<Future<HttpApi.Response>> futures = new ArrayList<>();
            for (int i = 0; i < tabs; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return api.rotate(refreshToken);
                }));
            }
            start.countDown();
            responses = new ArrayList<>();
            for (Future<HttpApi.Response> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(responses).allSatisfy(response -> assertThat(response.status()).isEqualTo(200));
        Set<String> newRefreshTokens = responses.stream().map(HttpApi.Response::refreshToken).collect(toSet());
        assertThat(newRefreshTokens).hasSize(1);
        assertThat(api.rotate(newRefreshTokens.iterator().next()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("유예 시간 안이라도 돌려줄 Refresh Token의 원래 값이 이미 사라졌으면, 세션을 폐기하지 않고 거절만 한다")
    void graceRefreshTokenAlreadyGoneIsRejectedWithoutRevokingSession() throws InterruptedException {
        TestUser user = users.create("correct-password");
        String previous = api.login(user.email(), user.password()).refreshToken();
        String latest = api.rotate(previous).refreshToken();

        // 서버 시계는 멈춰 두고 실제 시간만 10초 넘게 흘려, Redis가 원래 값을 먼저 지운 경계 상황을 만든다.
        Thread.sleep(GRACE_PERIOD.plusMillis(500).toMillis());

        api.rotate(previous).assertRefreshTokenRejected();
        assertThat(api.rotate(latest).status()).isEqualTo(200);
    }
}
