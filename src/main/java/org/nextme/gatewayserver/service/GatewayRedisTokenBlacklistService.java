package org.nextme.gatewayserver.service;

import lombok.RequiredArgsConstructor;
import org.nextme.common.jwt.TokenBlacklistService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class GatewayRedisTokenBlacklistService implements TokenBlacklistService {

    private static final String PREFIX = "blacklist:jwt:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String token, long millis) {
        if (millis <= 0) return;
        long seconds = Math.max(1L, millis / 1000L);
        redisTemplate.opsForValue()
                .set(PREFIX + token, "1", Duration.ofSeconds(seconds));
    }

    @Override
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}