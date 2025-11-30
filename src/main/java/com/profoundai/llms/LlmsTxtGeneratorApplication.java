package com.profoundai.llms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LlmsTxtGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(LlmsTxtGeneratorApplication.class, args);
	}

}
