package org.nextme.gatewayserver.config;
import org.nextme.common.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway에서 공통 JwtTokenProvider를 사용하기 위한 설정.
 * application.yml 의 jwt.* 값을 읽어서 JwtTokenProvider를 생성한다.
 */
@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-validity-seconds}")
    private long accessTokenValiditySeconds;

    @Value("${jwt.refresh-token-validity-seconds}")
    private long refreshTokenValiditySeconds;

    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        // msa-common의 JwtTokenProvider 사용
        return new JwtTokenProvider(
                secret,
                accessTokenValiditySeconds,
                refreshTokenValiditySeconds
        );
    }
}
