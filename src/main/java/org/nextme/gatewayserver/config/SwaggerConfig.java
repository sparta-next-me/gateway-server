package org.nextme.gatewayserver.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public GroupedOpenApi userAPI() {
        return GroupedOpenApi.builder()
                .group("user-api")
                .displayName("USER API")
                .pathsToMatch("/v1/user/**")
                .build();
    }

    @Bean
    public GroupedOpenApi promotionAPI() {
        return GroupedOpenApi.builder()
                .group("promotion-api")
                .displayName("PROMOTION API")
                .pathsToMatch("/v1/promotions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi pointAPI() {
        return GroupedOpenApi.builder()
                .group("point-api")
                .displayName("POINT API")
                .pathsToMatch("/v1/points/**")
                .build();
    }

    @Bean
    public GroupedOpenApi notificationAPI() {
        return GroupedOpenApi.builder()
                .group("notification-api")
                .displayName("NOTIFICATION API")
                .pathsToMatch("/v1/notifications/**")
                .build();
    }

    @Bean
    public GroupedOpenApi chatAPI() {
        return GroupedOpenApi.builder()
                .group("chat-api")
                .displayName("CHAT API")
                .pathsToMatch("/v1/chats/**")
                .build();
    }
}
