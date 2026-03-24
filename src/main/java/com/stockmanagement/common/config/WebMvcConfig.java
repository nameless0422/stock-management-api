package com.stockmanagement.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정적 SPA 페이지 라우팅 설정.
 * /shop, /shop/ 접근 시 index.html로 포워드한다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/shop").setViewName("forward:/shop/index.html");
        registry.addViewController("/shop/").setViewName("forward:/shop/index.html");
    }
}
