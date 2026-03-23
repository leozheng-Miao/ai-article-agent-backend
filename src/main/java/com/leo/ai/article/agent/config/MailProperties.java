package com.leo.ai.article.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mail")
public class MailProperties {
    private String from;
    private Long codeExpire;
    private Long codeCoolDown;
}