package com.specworkflow.me;

import com.specworkflow.auth.CurrentUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 사용자만 볼 수 있는 보호된 요청. 요청한 사용자의 식별자를 돌려준다. */
@RestController
class MeController {

    private final CurrentUser currentUser;

    MeController(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/me")
    MeResponse me() {
        return new MeResponse(currentUser.id());
    }

    record MeResponse(UUID userId) {}
}
