package com.jade.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一返回码
 *
 * 规范：
 *  0      成功
 *  1xxx   认证 / 权限
 *  4xxx   业务错误
 *  5xxx   系统错误
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, "success"),

    UNAUTHORIZED(1001, "未登录或登录已过期"),
    TOKEN_INVALID(1002, "token 无效"),
    FORBIDDEN(1003, "无权限访问"),
    CAPTCHA_ERROR(1102, "验证码错误"),

    BAD_REQUEST(4000, "请求参数错误"),
    VALIDATION_ERROR(4001, "参数校验失败"),
    BUSINESS_ERROR(4002, "业务异常"),

    NOT_FOUND(4004, "资源不存在"),
    CONFLICT(4009, "资源冲突"),

    INTERNAL_ERROR(5000, "服务器内部错误"),
    SERVICE_UNAVAILABLE(5003, "服务暂不可用");

    private final int code;
    private final String message;
}
