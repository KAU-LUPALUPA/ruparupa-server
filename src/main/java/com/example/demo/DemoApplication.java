package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemoApplication { 

    public static void main(String[] args) {
        // 실내 컴퓨터의 8080 포트
        SpringApplication.run(DemoApplication.class, args); 
    }

}