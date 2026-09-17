package com.jade.security.context;

import com.jade.common.exception.BizException;
import com.jade.common.constant.ResultCode;

/**
 * 当前登录用户上下文（基于 ThreadLocal）
 *
 * 用法：
 *   UserContext.set(userId, username);
 *   ... 业务逻辑 ...
 *   UserContext.clear();
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> CTX = new ThreadLocal<>();

    public record UserInfo(Long userId, String username) {}

    public static void set(Long userId, String username) {
        CTX.set(new UserInfo(userId, username));
    }

    public static UserInfo get() {
        return CTX.get();
    }

    public static Long getUserId() {
        UserInfo info = CTX.get();
        if (info == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return info.userId();
    }

    public static String getUsername() {
        UserInfo info = CTX.get();
        if (info == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return info.username();
    }

    public static void clear() {
        CTX.remove();
    }
}
