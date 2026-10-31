package com.lunixi.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SampleProperties.class)
public class CheckoutApplication {
    public static void main(String[] args) { SpringApplication.run(CheckoutApplication.class, args); }
}
