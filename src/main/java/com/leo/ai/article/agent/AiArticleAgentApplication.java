package com.leo.ai.article.agent;

import com.leo.ai.article.agent.config.MailProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableConfigurationProperties(MailProperties.class)
@MapperScan("com.leo.ai.article.agent.mapper")
public class AiArticleAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiArticleAgentApplication.class, args);
	}

}