package com.stockmanagement.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 설정.
 *
 * <p>접속 URL: {@code /swagger-ui/index.html}
 * <p>우측 상단 [Authorize] 버튼에서 로그인 후 발급받은 JWT를 입력하면
 * 인증이 필요한 엔드포인트를 Swagger UI에서 직접 호출할 수 있다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stock Management API")
                        .description("쇼핑몰 재고 관리 API — 재고 · 주문 · 결제 · 유저")
                        .version("v1.0"))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
