
package com.leo.ai.article.agent.service.impl;

import com.leo.ai.article.agent.config.MailProperties;
import com.leo.ai.article.agent.exception.BusinessException;
import com.leo.ai.article.agent.exception.ErrorCode;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.leo.ai.article.agent.constant.EmailConstant.CODE_PREFIX;
import static com.leo.ai.article.agent.constant.EmailConstant.COOL_DOWN_PREFIX;
import static com.leo.ai.article.agent.constant.EmailConstant.SCENE_LOGIN;
import static com.leo.ai.article.agent.constant.EmailConstant.SCENE_REGISTER;
import static com.leo.ai.article.agent.constant.EmailConstant.SCENE_RESET;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final MailProperties mailProperties;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;



    public void sendVerifyCode(String email, String scene) {
        String coolDownKey = COOL_DOWN_PREFIX + scene + ":" + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(coolDownKey))) {
            Long ttl = redisTemplate.getExpire(coolDownKey, TimeUnit.SECONDS);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "操作过于频繁，请 " + ttl + " 秒后再试");
        }
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        String codeKey = CODE_PREFIX + scene + ":" + email;
        redisTemplate.opsForValue().set(codeKey, code, mailProperties.getCodeExpire(), TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(coolDownKey, "1", mailProperties.getCodeCoolDown(), TimeUnit.SECONDS);
        doSend(email, code, scene);
        log.info("[EmailService] 验证码已发送 email={} scene={}", email, scene);
    }

    public void verifyCode(String email, String code, String scene) {
        String codeKey = CODE_PREFIX + scene + ":" + email;
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码已过期，请重新获取");
        }
        if (!stored.equals(code)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误");
        }
        redisTemplate.delete(codeKey);
    }

    private void doSend(String toEmail, String code, String scene) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.getFrom());
            helper.setTo(toEmail);
            helper.setSubject(buildSubject(scene));
            helper.setText(buildContent(code, scene), true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("[EmailService] 邮件发送失败 email={} error={}", toEmail, e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "邮件发送失败，请稍后重试");
        }
    }

    private String buildSubject(String scene) {
        return switch (scene) {
            case SCENE_LOGIN    -> "【登录验证】您的登录验证码";
            case SCENE_REGISTER -> "【注册验证】您的注册验证码";
            case SCENE_RESET    -> "【重置密码】您的密码重置验证码";
            default             -> "【验证码】";
        };
    }

    private String buildContent(String code, String scene) {
        String action = switch (scene) {
            case SCENE_LOGIN    -> "登录";
            case SCENE_REGISTER -> "注册";
            case SCENE_RESET    -> "重置密码";
            default             -> "操作";
        };
        long expireMin = mailProperties.getCodeExpire() / 60;
        return """
            <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:24px;border:1px solid #eee;border-radius:8px;">
              <h2 style="color:#333;margin-bottom:8px;">%s验证码</h2>
              <p style="color:#666;">您正在进行 <strong>%s</strong> 操作，验证码为：</p>
              <div style="background:#f7f7f7;padding:16px;border-radius:6px;text-align:center;margin:16px 0;">
                <span style="font-size:36px;font-weight:bold;color:#e74c3c;letter-spacing:10px;">%s</span>
              </div>
              <p style="color:#999;">验证码 <strong>%d 分钟</strong>内有效，请勿泄露给他人。</p>
              <p style="color:#ccc;font-size:12px;">如非本人操作，请忽略此邮件。</p>
            </div>
            """.formatted(action, action, code, expireMin);
    }
}