package com.jade.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 验证码 id（后台开启验证码且失败次数达到阈值时必填）
     */
    private String captchaId;

    /**
     * 验证码答案
     */
    private String captchaCode;
}
