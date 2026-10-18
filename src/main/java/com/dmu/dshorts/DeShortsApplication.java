package com.dmu.dshorts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DeShortsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeShortsApplication.class, args);
    }
}
