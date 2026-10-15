package com.specworkflow.support;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 테스트용 사용자를 준비한다. 사용자를 만드는 일은 로그인 기능 밖(회원가입 spec)이므로
 * 테스트 준비 단계에서만 데이터베이스에 직접 넣는다.
 */
public class TestUsers {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final JdbcClient jdbc;

    public TestUsers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 무작위 이메일의 사용자를 만들고 그 이메일을 돌려준다. */
    public TestUser create(String password) {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        UUID id = jdbc.sql("insert into users (email, password_hash) values (?, ?) returning id")
                .params(email, BCRYPT.encode(password))
                .query(UUID.class)
                .single();
        return new TestUser(id, email, password);
    }

    public record TestUser(UUID id, String email, String password) {}
}
