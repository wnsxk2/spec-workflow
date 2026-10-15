package com.specworkflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.specworkflow.support.HttpApi;
import com.specworkflow.support.HttpApiTest;
import com.specworkflow.support.TestClock;
import com.specworkflow.support.TestUsers;
import com.specworkflow.support.TestUsers.TestUser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@HttpApiTest
@DisplayName("보호된 요청")
class ProtectedRequestHttpApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
    @DisplayName("유효한 Access Token으로 보호된 요청을 하면 요청한 사용자의 식별자를 알 수 있다")
    void protectedRequestKnowsTheUser() {
        TestUser user = users.create("correct-password");
        String accessToken = api.login(user.email(), user.password()).accessToken();

        HttpApi.Response response = api.get("/api/me", accessToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("userId").asString()).isEqualTo(user.id().toString());
    }
    @Test
    @DisplayName("Access Token이 없으면 401로 거절한다")
    void missingAccessTokenIsRejected() {
        assertThat(api.get("/api/me", null).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("다른 비밀 키로 서명한 위조 Access Token은 401로 거절한다")
    void forgedAccessTokenIsRejected() throws Exception {
        TestUser user = users.create("correct-password");
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.id().toString())
                .issueTime(Date.from(clock.instant()))
                .expirationTime(Date.from(clock.instant().plus(Duration.ofMinutes(15))))
                .build();
        SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        forged.sign(new MACSigner("another-secret-key-that-is-at-least-32-characters".getBytes(StandardCharsets.UTF_8)));

        assertThat(api.get("/api/me", forged.serialize()).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Access Token은 발급 15분 뒤부터 401로 거절한다")
    void accessTokenExpiresAfter15Minutes() {
        TestUser user = users.create("correct-password");
        String accessToken = api.login(user.email(), user.password()).accessToken();

        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(api.get("/api/me", accessToken).status()).isEqualTo(200);

        clock.advance(Duration.ofSeconds(1));
        assertThat(api.get("/api/me", accessToken).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("Access Token은 HS256으로 서명하고 사용자 식별자, 발급 시각, 만료 시각만 담는다")
    void accessTokenContents() {
        TestUser user = users.create("correct-password");
        String[] parts = api.login(user.email(), user.password()).accessToken().split("\\.");

        JsonNode header = JSON.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));

        assertThat(header.get("alg").asString()).isEqualTo("HS256");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder("sub", "iat", "exp");
        assertThat(payload.get("sub").asString()).isEqualTo(user.id().toString());
        assertThat(payload.get("iat").asLong()).isEqualTo(clock.instant().getEpochSecond());
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong()).isEqualTo(15 * 60);
    }
}
