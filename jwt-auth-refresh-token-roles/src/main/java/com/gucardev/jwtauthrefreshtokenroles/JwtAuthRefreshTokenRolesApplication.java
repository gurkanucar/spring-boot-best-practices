package com.gucardev.jwtauthrefreshtokenroles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JwtAuthRefreshTokenRolesApplication {

    public static void main(String[] args) {
        SpringApplication.run(JwtAuthRefreshTokenRolesApplication.class, args);
    }
}
