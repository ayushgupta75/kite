package com.ayush.kite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/auth/**")
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(true);

        registry.addMapping("/orders/**")
                .allowedOrigins(frontendUrl)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
