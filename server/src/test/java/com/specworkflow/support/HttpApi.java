package com.specworkflow.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 서버에 실제 HTTP 요청을 보내는 테스트 도구. 쿠키는 자동으로 저장하지 않는다. */
public class HttpApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;

    public HttpApi(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public Response login(String email, String password) {
        return post("/api/auth/login", JSON.writeValueAsString(Map.of("email", email, "password", password)));
    }

    public Response post(String path, String jsonBody) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)));
    }

    /** 토큰 교환. refreshToken이 null이면 Refresh Token 쿠키 없이 요청한다. */
    public Response rotate(String refreshToken) {
        return postWithRefreshToken("/api/auth/refresh", refreshToken);
    }

    /** 로그아웃. refreshToken이 null이면 Refresh Token 쿠키 없이 요청한다. */
    public Response logout(String refreshToken) {
        return postWithRefreshToken("/api/auth/logout", refreshToken);
    }

    private Response postWithRefreshToken(String path, String refreshToken) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (refreshToken != null) {
            request.header("Cookie", "refresh_token=" + refreshToken);
        }
        return send(request);
    }

    public Response get(String path, String accessToken) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return send(request);
    }

    private Response send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = client.send(
                    request.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public record Response(HttpResponse<String> raw) {

        public int status() {
            return raw.statusCode();
        }

        public JsonNode json() {
            return JSON.readTree(raw.body());
        }

        public String accessToken() {
            return json().get("accessToken").asString();
        }

        /** 응답이 설정한 Refresh Token 쿠키의 값. */
        public String refreshToken() {
            return cookie("refresh_token").orElseThrow().value();
        }

        /** 토큰 교환이 401로 거절되고 Refresh Token 쿠키를 지우라는 응답인지 확인한다. */
        public void assertRefreshTokenRejected() {
            assertThat(status()).isEqualTo(401);
            assertRefreshTokenCookieDeleted();
        }

        /** 로그아웃이 204로 성공하고 Refresh Token 쿠키를 지우라는 응답인지 확인한다. */
        public void assertLogoutSucceeded() {
            assertThat(status()).isEqualTo(204);
            assertRefreshTokenCookieDeleted();
        }

        /** 발급할 때와 같은 이름·경로의 Refresh Token 쿠키를 지우라는 응답인지 확인한다. */
        public void assertRefreshTokenCookieDeleted() {
            SetCookie cookie = cookie("refresh_token").orElseThrow();
            assertThat(cookie.value()).isEmpty();
            assertThat(cookie.attribute("Max-Age")).hasValue("0");
            assertThat(cookie.attribute("Path")).hasValue("/api/auth");
        }

        public Optional<SetCookie> cookie(String name) {
            return raw.headers().allValues("Set-Cookie").stream()
                    .map(SetCookie::parse)
                    .filter(cookie -> cookie.name().equals(name))
                    .findFirst();
        }
    }
}
