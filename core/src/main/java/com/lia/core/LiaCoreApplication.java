package com.lia.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan   // LiaSourceProperties 등 record 프로퍼티 바인딩
public class LiaCoreApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(LiaCoreApplication.class, args);
        // 배치(D62)는 한 번 돌고 끝나는 잡 — 실행 결과를 종료 코드로 돌려준다(외부 스케줄러 알림용)
        if (context.getEnvironment().matchesProfiles("batch")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
