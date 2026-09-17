package com.jade.admin.controller;

import com.jade.admin.dto.LoginRequest;
import com.jade.admin.dto.LoginResponse;
import com.jade.admin.dto.ForgotPasswordRequest;
import com.jade.admin.dto.ResetPasswordRequest;
import com.jade.admin.service.AuthService;
import com.jade.admin.service.CaptchaService;
import com.jade.admin.service.PasswordResetService;
import com.jade.common.api.R;
import com.jade.security.entity.SysUser;
import com.jade.security.jwt.MpJwtUtil;
import com.jade.security.repository.SysUserRepository;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "认证管理")
public class AuthController {

    @Inject
    AuthService authService;

    @Inject
    CaptchaService captchaService;

    @Inject
    PasswordResetService passwordResetService;

    @Inject
    SysUserRepository userRepository;

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    @Inject
    MpJwtUtil mpJwtUtil;

    /** 验证码开关状态（enabled）与该用户名是否已触发强制验证码（required） */
    public record CaptchaStatus(boolean enabled, boolean required) {}

    @GET
    @Path("/captcha/status")
    @PermitAll
    @Operation(summary = "验证码配置状态", description = "enabled=后台开启验证码；required=该用户名失败次数已达标，登录必须携带验证码")
    public R<CaptchaStatus> captchaStatus(@QueryParam("username") String username) {
        boolean enabled = captchaService.isEnabled();
        boolean required = enabled && captchaService.isRequired(username == null ? "" : username);
        return R.ok(new CaptchaStatus(enabled, required));
    }

    @GET
    @Path("/captcha/new")
    @PermitAll
    @Operation(summary = "生成验证码", description = "返回 captchaId 与 SVG 图片；后台未开启验证码时返回 4004")
    public R<CaptchaService.CaptchaImage> newCaptcha() {
        if (!captchaService.isEnabled()) {
            return R.fail(4004, "验证码未启用");
        }
        return R.ok(captchaService.generate());
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "登录", description = "返回 JWT token")
    public R<LoginResponse> login(@Valid LoginRequest req, @Context HttpHeaders headers) {
        String ip = clientIp(headers);
        return authService.login(req, ip);
    }

    @GET
    @Path("/me")
    @Authenticated
    @Operation(summary = "当前登录用户信息")
    public R<SysUser> me() {
        if (identity == null || identity.isAnonymous() || identity.getPrincipal() == null) {
            return R.fail(401, "未登录");
        }
        String username = identity.getPrincipal().getName();
        SysUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return R.fail(404, "用户不存在");
        }
        user.password = null;
        return R.ok(user);
    }

    @PUT
    @Path("/profile")
    @Authenticated
    @Transactional
    @Operation(summary = "更新当前用户个人信息")
    public R<SysUser> updateProfile(SysUser req) {
        if (identity == null || identity.isAnonymous()) return R.fail(401, "未登录");
        String username = identity.getPrincipal().getName();
        SysUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return R.fail(404, "用户不存在");
        if (req.nickname != null) user.nickname = req.nickname;
        if (req.email != null) user.email = req.email;
        if (req.phone != null) user.phone = req.phone;
        userRepository.persist(user);
        user.password = null;
        return R.ok(user);
    }

    @PUT
    @Path("/password")
    @Authenticated
    @Transactional
    @Operation(summary = "修改自己的密码")
    public R<Void> changePassword(@QueryParam("old") String oldPwd, @QueryParam("new") String newPwd) {
        if (identity == null || identity.isAnonymous()) return R.fail(401, "未登录");
        if (oldPwd == null || newPwd == null) {
            return R.fail(400, "旧密码和新密码必填");
        }
        String username = identity.getPrincipal().getName();
        SysUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return R.fail(404, "用户不存在");
        at.favre.lib.crypto.bcrypt.BCrypt.Result result = at.favre.lib.crypto.bcrypt.BCrypt.verifyer()
                .verify(oldPwd.toCharArray(), user.password);
        if (!result.verified) {
            return R.fail(400, "旧密码错误");
        }
        String passwordHash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
                .hashToString(10, newPwd.toCharArray());
        if (userRepository.updatePassword(user.id, passwordHash) != 1) {
            return R.fail(500, "密码更新失败");
        }
        return R.ok();
    }

    @POST
    @Path("/forgot-password")
    @PermitAll
    @Blocking
    @Operation(summary = "忘记密码 · 发送邮箱验证码", description = "邮箱存在则发送验证码（60s 冷却）；不存在也返回成功以防枚举")
    public Uni<R<Void>> forgotPassword(@Valid ForgotPasswordRequest req) {
        return passwordResetService.sendResetCode(req.getEmail()).onItem().transform(v -> R.ok());
    }

    @POST
    @Path("/reset-password")
    @PermitAll
    @Transactional
    @Operation(summary = "忘记密码 · 验证码重置密码", description = "校验邮箱验证码并重置密码，返回账号用户名")
    public R<String> resetPassword(@Valid ResetPasswordRequest req) {
        return R.ok(passwordResetService.reset(req));
    }

    @POST
    @Path("/logout")
    @Authenticated
    @Operation(summary = "登出（前端清 token 即可）")
    public R<Void> logout() {
        return R.ok();
    }

    private String clientIp(HttpHeaders headers) {
        String ip = headers.getHeaderString("X-Forwarded-For");
        if (ip == null) ip = headers.getHeaderString("X-Real-IP");
        if (ip == null) ip = "127.0.0.1";
        return ip.split(",")[0].trim();
    }
}
