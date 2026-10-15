package com.specworkflow.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.specworkflow.Application;
import com.specworkflow.support.TestInfrastructure;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

@DisplayName("서명용 비밀 키")
class JwtSecretStartupTest {

    @Test
    @DisplayName("서명용 비밀 키가 없으면 서버가 시작되지 않는다")
    void serverDoesNotStartWithoutSecret() {
        assertThatThrownBy(() -> start("auth.jwt.secret="))
                .hasStackTraceContaining("AUTH_JWT_SECRET");
    }

    @Test
    @DisplayName("서명용 비밀 키가 32자보다 짧으면 서버가 시작되지 않는다")
    void serverDoesNotStartWithShortSecret() {
        assertThatThrownBy(() -> start("auth.jwt.secret=too-short"))
                .hasStackTraceContaining("32자 이상");
    }

    private static void start(String secretProperty) {
        new SpringApplicationBuilder(Application.class, TestInfrastructure.class)
                .properties("server.port=0", secretProperty)
                .run()
                .close();
    }
}
