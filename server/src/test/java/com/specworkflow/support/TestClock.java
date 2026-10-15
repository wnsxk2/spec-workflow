package com.specworkflow.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 시간을 앞당길 수 있는 시계. */
public class TestClock extends Clock {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private volatile Instant now = START;

    public void reset() {
        now = START;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
