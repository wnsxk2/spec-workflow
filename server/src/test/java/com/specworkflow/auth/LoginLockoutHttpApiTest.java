package com.specworkflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.specworkflow.support.HttpApi;
import com.specworkflow.support.HttpApiTest;
import com.specworkflow.support.TestClock;
import com.specworkflow.support.TestUsers;
import com.specworkflow.support.TestUsers.TestUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
@DisplayName("로그인 차단")
class LoginLockoutHttpApiTest {

    private static final String LOGIN_FAILURE_MESSAGE = "이메일 또는 비밀번호가 올바르지 않습니다";

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
    @DisplayName("같은 이메일로 4번째 실패까지는 401 공통 메시지를, 5번째 실패에는 429와 로그인 차단 안내를 받는다")
    void fifthFailureIsLoginLockout() {
        TestUser user = users.create("correct-password");

        for (int failure = 1; failure <= 4; failure++) {
            assertLoginFailure(api.login(user.email(), "wrong-password"));
        }
        HttpApi.Response fifth = api.login(user.email(), "wrong-password");

        assertLoginLockout(fifth);
    }

    @Test
    @DisplayName("존재하지 않는 이메일도 같은 규칙으로 세어 5번째 실패에 로그인 차단한다")
    void unknownEmailIsCountedTheSameWay() {
        String unknownEmail = "nobody-" + UUID.randomUUID() + "@example.com";

        for (int failure = 1; failure <= 4; failure++) {
            assertLoginFailure(api.login(unknownEmail, "any-password"));
        }

        assertLoginLockout(api.login(unknownEmail, "any-password"));
    }

    @Test
    @DisplayName("앞뒤 공백과 대소문자만 다른 이메일은 같은 이메일로 센다")
    void emailIsNormalizedForCounting() {
        TestUser user = users.create("correct-password");
        String upper = user.email().toUpperCase();

        assertLoginFailure(api.login(user.email(), "wrong-password"));
        assertLoginFailure(api.login(" " + user.email(), "wrong-password"));
        assertLoginFailure(api.login(upper, "wrong-password"));
        assertLoginFailure(api.login(upper + "  ", "wrong-password"));

        assertLoginLockout(api.login("  " + upper, "wrong-password"));
    }

    @Test
    @DisplayName("로그인 차단 중에는 올바른 비밀번호도 429로 거절한다")
    void correctPasswordIsRejectedWhileLocked() {
        TestUser user = users.create("correct-password");
        lockOut(user);

        assertLoginLockout(api.login(user.email(), user.password()));
    }

    @Test
    @DisplayName("로그인 차단은 5번째 실패 시점부터 15분 동안이고, 그 뒤에는 따로 할 일 없이 다시 로그인할 수 있다")
    void lockoutEndsFifteenMinutesAfterFifthFailure() {
        TestUser user = users.create("correct-password");
        lockOut(user);

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertLoginLockout(api.login(user.email(), user.password()));

        clock.advance(Duration.ofSeconds(1));
        assertThat(api.login(user.email(), user.password()).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("마지막 실패로부터 15분이 지나면 실패 횟수가 사라진다")
    void failuresExpireFifteenMinutesAfterLastFailure() {
        TestUser user = users.create("correct-password");
        failFourTimes(user.email());

        clock.advance(Duration.ofMinutes(15));

        // 이전 4번이 남아 있었다면 다음 실패가 5번째가 되어 로그인 차단이다.
        for (int failure = 1; failure <= 4; failure++) {
            assertLoginFailure(api.login(user.email(), "wrong-password"));
        }
    }

    @Test
    @DisplayName("15분이 지나기 전에 다시 실패하면 횟수가 이어지고, 실패 기록은 그 마지막 실패부터 15분을 다시 센다")
    void failureBeforeExpiryKeepsCountingFromLastFailure() {
        TestUser user = users.create("correct-password");
        assertLoginFailure(api.login(user.email(), "wrong-password"));

        clock.advance(Duration.ofMinutes(10));
        for (int failure = 2; failure <= 4; failure++) {
            assertLoginFailure(api.login(user.email(), "wrong-password"));
        }

        // 첫 실패로부터는 20분이 지났지만 마지막 실패로부터는 10분이다.
        clock.advance(Duration.ofMinutes(10));
        assertLoginLockout(api.login(user.email(), "wrong-password"));
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 기록을 지워, 이후 실패는 이전 횟수와 합쳐지지 않는다")
    void successfulLoginClearsFailures() {
        TestUser user = users.create("correct-password");
        failFourTimes(user.email());

        assertThat(api.login(user.email(), user.password()).status()).isEqualTo(200);

        for (int failure = 1; failure <= 4; failure++) {
            assertLoginFailure(api.login(user.email(), "wrong-password"));
        }
    }

    @Test
    @DisplayName("4번 실패한 뒤 동시에 들어온 로그인 시도는 5번째 시도 하나만 비밀번호를 대조받고, 나머지는 로그인 차단으로 거절한다")
    void concurrentAttemptsCannotBypassLoginLockout() throws Exception {
        TestUser user = users.create("correct-password");
        failFourTimes(user.email());
        int attempts = 10;
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            List<Future<HttpApi.Response>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return api.login(user.email(), user.password());
                }));
            }
            start.countDown();
            for (Future<HttpApi.Response> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS).status());
            }
        }

        // 비밀번호를 대조받은 5번째 시도만 로그인에 성공한다. 동시에 들어온 나머지는 그동안 로그인 차단이다.
        assertThat(statuses).containsOnly(200, 429);
        assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
    }

    private void lockOut(TestUser user) {
        failFourTimes(user.email());
        assertLoginLockout(api.login(user.email(), "wrong-password"));
    }

    private void failFourTimes(String email) {
        for (int failure = 1; failure <= 4; failure++) {
            assertLoginFailure(api.login(email, "wrong-password"));
        }
    }

    private static void assertLoginFailure(HttpApi.Response response) {
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.json().get("message").asString()).isEqualTo(LOGIN_FAILURE_MESSAGE);
    }

    private static void assertLoginLockout(HttpApi.Response response) {
        assertThat(response.status()).isEqualTo(429);
        assertThat(response.json().get("message").asString()).contains("잠시 후 다시 시도").isNotEqualTo(LOGIN_FAILURE_MESSAGE);
        assertThat(response.cookie("refresh_token")).isEmpty();
    }
}
