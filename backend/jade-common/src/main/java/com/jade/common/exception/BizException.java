package com.jade.common.exception;

import com.jade.common.constant.ResultCode;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final String message;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
        this.message = message;
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.message = message;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}
