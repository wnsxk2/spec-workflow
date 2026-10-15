package com.specworkflow.auth;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 보호된 요청을 처리할 때 지금 요청한 사용자가 누구인지 알려 준다.
 * Access Token의 내용은 인증 모듈 안에 숨기고, 밖에는 사용자 식별자만 내보낸다.
 */
@Component
public class CurrentUser {

    /** 지금 요청한 사용자의 식별자. 보호된 요청 안에서만 부른다. */
    public UUID id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken accessToken)) {
            throw new IllegalStateException("보호된 요청 안에서만 요청한 사용자를 알 수 있습니다");
        }
        return UUID.fromString(accessToken.getToken().getSubject());
    }
}
