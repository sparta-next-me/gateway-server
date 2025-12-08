package org.nextme.gatewayserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nextme.common.jwt.JwtTokenProvider;
import org.nextme.common.jwt.TokenBlacklistService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 *  JwtGatewayFilter
 *
 * Gateway 단에서 Authorization 헤더에 JWT 가 있으면:
 *  - 토큰을 검증하고
 *  - 유효한 access 토큰이면 X-User-Id / X-User-Roles 헤더를 추가해서
 *    뒤에 있는 마이크로서비스(user-service 등)로 넘겨주는 필터.
 *
 * 동작 요약:
 *  1) Authorization 헤더가 아예 없거나 "Bearer " 로 시작하지 않으면
 *      아무 검증도 하지 않고 그대로 다음 필터로 통과.
 *  2) Authorization 헤더가 "Bearer xxx" 형태라면
 *          validateToken() 으로 서명/만료 등을 검증
 *         - 실패 : 401 UNAUTHORIZED 반환
 *  3) tokenType 이 "access" 가 아니면 (예: refresh)
 *           401 UNAUTHORIZED
 *  4) userId / roles 를 토큰에서 꺼내서
 *          X-User-Id, X-User-Roles 커스텀 헤더로 추가한 뒤 체인 계속 진행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    private static final List<String> TOKEN_API_WHITELIST = List.of(
            "/v1/user/auth/refresh",
            "/v1/user/auth/logout"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        // 디버깅용: 어떤 요청이 들어왔는지 로그
        String path = request.getURI().getPath();
        log.info("[JwtGatewayFilter] >>> Incoming path = {}", path);

        // refresh/logout 같은 토큰 API는 게이트웨이에서 JWT 검사 안 함
        if (TOKEN_API_WHITELIST.stream().anyMatch(path::startsWith)) {
            log.info("[JwtGatewayFilter] >>> WHITELISTED. Skip JWT validation. path={}", path);
            return chain.filter(exchange);
        }

        log.info("[JwtGatewayFilter] >>> NOT WHITELISTED. Will validate JWT. path={}", path);

        // 1. Authorization 헤더에서 Bearer 토큰 추출
        String token = resolveToken(request);

        // 2. 토큰이 없으면 → JWT 인증을 요구하지 않는 공개 API 라고 보고 그냥 통과
        if (!StringUtils.hasText(token)) {
            log.debug("[JwtGatewayFilter] No Authorization Bearer token found. Skipping JWT validation.");
            return chain.filter(exchange);
        }

        log.debug("[JwtGatewayFilter] Bearer token found. Start validation.");

        try {
            // 3. 토큰 기본 검증 (서명, 만료 등)
            boolean valid = jwtTokenProvider.validateToken(token);
            if (!valid) {
                log.debug("[JwtGatewayFilter] Token validation failed. Returning 401.");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 3-1. 블랙리스트 체크
            if (tokenBlacklistService.isBlacklisted(token)) {
                log.debug("[JwtGatewayFilter] Token is blacklisted. Returning 401.");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 4. 토큰 타입이 access 인지 확인 (refresh 토큰이면 거부)
            String tokenType = jwtTokenProvider.getTokenType(token);
            log.debug("[JwtGatewayFilter] tokenType = {}", tokenType);

            if (!"access".equals(tokenType)) {
                log.debug("[JwtGatewayFilter] Token is not access type. Returning 401.");
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 5. 토큰에서 userId / roles 추출
            String userId = jwtTokenProvider.getUserId(token);
            List<String> roles = jwtTokenProvider.getRoles(token);

            log.debug("[JwtGatewayFilter] Token validated. userId = {}, roles = {}", userId, roles);

            // 6. 기존 요청을 복제(mutate)해서 커스텀 헤더 추가
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)                         // 예: UUID 문자열
                    .header("X-User-Roles", String.join(",", roles))     // 예: "USER,ADVISOR"
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

            // 7. 수정된 요청으로 다음 필터/라우트로 진행
            return chain.filter(mutatedExchange);

        } catch (Exception e) {
            // JwtTokenProvider 내부에서 예외가 터져도 500 대신 401로 응답 + 로그 남김
            log.warn("[JwtGatewayFilter] Exception while validating JWT. Returning 401. message={}",
                    e.getMessage(), e);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * Authorization 헤더에서 "Bearer xxx" 형식의 토큰을 추출.
     *
     * @param request 현재 HTTP 요청
     * @return "xxx" (실제 토큰 문자열) 또는 토큰이 없으면 null
     */
    private String resolveToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 디버깅용으로 원본 헤더도 한 번 찍어보기 (길면 잘려 보이긴 함)
        log.debug("[JwtGatewayFilter] Authorization header raw = {}", bearer);

        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    /**
     * GlobalFilter 의 실행 순서.
     * 숫자가 작을수록 더 먼저 실행됨.
     *
     * 여기서는 HIGHEST_PRECEDENCE 로 설정해서
     * 웬만한 필터보다 가장 먼저 JWT 검증을 수행하도록 함.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
