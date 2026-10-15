package com.specworkflow;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 서버가 지금 시각을 아는 곳. 테스트에서는 다른 시계로 바꿔 끼운다. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
