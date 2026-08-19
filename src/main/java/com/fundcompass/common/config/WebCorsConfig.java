package com.fundcompass.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트엔드(Next.js)가 다른 오리진에서 API를 부르므로 CORS가 필요하다.
 * 로컬은 :3000, 배포는 Vercel 도메인이라 환경변수로 받는다.
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Value("${fundcompass.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
