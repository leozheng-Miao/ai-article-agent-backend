// SendEmailCodeRequest.java
package com.leo.ai.article.agent.model.dto.user.email;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.io.Serializable;

@Data
public class SendEmailCodeRequest implements Serializable {
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "场景不能为空")
    @Pattern(regexp = "^(login|register|reset)$", message = "场景参数非法")
    private String scene;
}