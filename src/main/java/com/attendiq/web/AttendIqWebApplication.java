package com.attendiq.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AttendIqWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(AttendIqWebApplication.class, args);
    }
}
