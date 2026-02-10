package com.thock.back.gateway;

import com.thock.back.global.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import javax.swing.*;

@SpringBootApplication(scanBasePackages = {
        "com.thock.back.gateway",
        "com.thock.back.global",
})
@EnableConfigurationProperties(JwtProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
