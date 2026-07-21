package com.lia.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // LiaSourceProperties 등 record 프로퍼티 바인딩
public class LiaCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(LiaCoreApplication.class, args);
    }
}
