
// UserEmailRegisterRequest.java
package com.leo.ai.article.agent.model.dto.user.email;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.io.Serializable;

@Data
public class UserEmailRegisterRequest implements Serializable {
    @NotBlank @Email
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "密码不能为空")
    private String userPassword;

    @NotBlank(message = "确认密码不能为空")
    private String checkPassword;
}