package com.specworkflow.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 사용자 정보에서 로그인에 필요한 것만 읽는다. 쓰지 않는다. */
@Component
class UserCredentials {

    private final JdbcClient jdbc;

    UserCredentials(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<StoredCredential> findByEmail(String normalizedEmail) {
        return jdbc.sql("select id, password_hash from users where email = ?")
                .param(normalizedEmail)
                .query((rs, row) -> new StoredCredential(rs.getObject("id", UUID.class), rs.getString("password_hash")))
                .optional();
    }

    record StoredCredential(UUID userId, String passwordHash) {}
}
