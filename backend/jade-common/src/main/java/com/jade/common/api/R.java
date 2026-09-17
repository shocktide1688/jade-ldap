package com.jade.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jade.common.constant.ResultCode;
import lombok.Data;

/**
 * 统一响应包装
 *
 * @param <T> 数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {

    private int code;
    private String message;
    private T data;
    private Long timestamp = System.currentTimeMillis();

    public R() {}

    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> R<T> fail(ResultCode resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }

    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
