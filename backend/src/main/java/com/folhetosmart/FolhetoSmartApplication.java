package com.folhetosmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FolhetoSmartApplication {

    public static void main(String[] args) {
        SpringApplication.run(FolhetoSmartApplication.class, args);
    }
}
