package com.jade.common.exception;

import com.jade.common.api.R;
import com.jade.common.constant.ResultCode;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 */
@Slf4j
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable e) {
        // 业务异常
        if (e instanceof BizException biz) {
            log.warn("[BizException] code={} msg={}", biz.getCode(), biz.getMessage());
            return Response.status(Response.Status.OK)
                    .entity(R.fail(biz.getCode(), biz.getMessage()))
                    .build();
        }

        // JAX-RS 框架异常（404 / 405 等）
        if (e instanceof NotFoundException) {
            return Response.status(RestResponse.StatusCode.NOT_FOUND)
                    .entity(R.fail(ResultCode.NOT_FOUND, "请求路径不存在"))
                    .build();
        }

        if (e instanceof WebApplicationException wae) {
            int status = wae.getResponse().getStatus();
            String msg = wae.getMessage() == null ? "请求错误" : wae.getMessage();
            return Response.status(status)
                    .entity(R.fail(status, msg))
                    .build();
        }

        // 参数校验异常
        if (e instanceof ConstraintViolationException cve) {
            String msg = cve.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            log.warn("[Validation] {}", msg);
            return Response.status(RestResponse.StatusCode.BAD_REQUEST)
                    .entity(R.fail(ResultCode.VALIDATION_ERROR, msg))
                    .build();
        }

        // 兜底
        log.error("[Internal] ", e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(R.fail(ResultCode.INTERNAL_ERROR, "服务器内部错误"))
                .build();
    }
}
