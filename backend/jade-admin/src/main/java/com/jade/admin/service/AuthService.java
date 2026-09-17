package com.jade.admin.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.jade.admin.dto.LoginRequest;
import com.jade.admin.dto.LoginResponse;
import com.jade.admin.listener.LoginLogListener.LoginEvent;
import com.jade.admin.metrics.BusinessMetrics;
import com.jade.admin.repository.SysRoleRepository;
import com.jade.admin.repository.SysUserRoleRepository;
import com.jade.common.api.R;
import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.security.entity.SysUser;
import com.jade.security.jwt.MpJwtUtil;
import com.jade.security.repository.SysUserRepository;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证服务
 *
 * 安全审计修复点：
 *   - 统一使用 BCrypt 验证（不再有明文密码分支）
 *   - 移除"固定密码 admin123"绕过逻辑
 *   - 统一错误消息（"用户名或密码错误"，不暴露用户是否存在）
 *   - 登录失败抛 401
 *   - 计时攻击防护：无论用户是否存在都执行一次 BCrypt.verify
 *
 * v2 改进：
 *   - 登录后异步写 sys_login_log（CDI event 解耦）
 *   - 从 sys_user_role 读真实角色，不再写死 admin/user
 */
@ApplicationScoped
public class AuthService {

    @Inject
    SysUserRepository userRepository;

    @Inject
    SysRoleRepository roleRepository;

    @Inject
    SysUserRoleRepository userRoleRepository;

    @Inject
    MpJwtUtil mpJwtUtil;

    @Inject
    BusinessMetrics metrics;

    @Inject
    CaptchaService captchaService;

    @Inject
    Event<LoginEvent> loginEvent;

    @ConfigProperty(name = "jade.jwt.expire-seconds", defaultValue = "7200")
    long expireSeconds;

    public R<LoginResponse> login(LoginRequest req, String ip) {
        Timer.Sample sample = Timer.start();
        try {
            // 0) 验证码：开关开启且该用户名失败次数达到阈值时强制校验（密码验证前，防爆破）
            if (captchaService.isRequired(req.getUsername())
                    && !captchaService.verify(req.getCaptchaId(), req.getCaptchaCode())) {
                throw new BizException(ResultCode.CAPTCHA_ERROR, "验证码错误");
            }

            // 1) 查用户
            SysUser user = userRepository.findByUsername(req.getUsername()).orElse(null);

            // 2) 校验密码
            boolean valid = verifyPassword(user, req.getPassword());

            if (user == null || !valid) {
                metrics.recordLoginFailure();
                captchaService.recordFail(req.getUsername());
                loginEvent.fire(new LoginEvent(req.getUsername(), ip, false, "用户名或密码错误"));
                throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
            }

            // 3) 账号状态
            if (user.status == 0) {
                metrics.recordLoginFailure();
                captchaService.recordFail(req.getUsername());
                loginEvent.fire(new LoginEvent(req.getUsername(), ip, false, "账号已被禁用"));
                throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
            }

            // 4) 查角色（从关联表）
            Set<String> roles = loadUserRoles(user.id);

            // 5) 生成 token
            String token = mpJwtUtil.generate(
                    user.id,
                    user.username,
                    roles,
                    user.tenantId == null ? 0L : user.tenantId
            );

            metrics.recordLoginSuccess();
            captchaService.resetFail(req.getUsername());
            loginEvent.fire(new LoginEvent(req.getUsername(), ip, true, "登录成功"));

            return R.ok(new LoginResponse(
                    token,
                    "Bearer",
                    expireSeconds,
                    new LoginResponse.UserInfo(user.id, user.username, user.nickname, user.email)
            ));
        } finally {
            sample.stop(metrics.loginTimer());
        }
    }

    @Transactional
    public Set<String> loadUserRoles(Long userId) {
        List<com.jade.admin.entity.SysRole> roleList = roleRepository.listByUserId(userId);
        Set<String> roleCodes = new HashSet<>();
        for (var role : roleList) {
            roleCodes.add(role.roleCode);
        }
        // 默认给个 user 角色（避免空）
        if (roleCodes.isEmpty()) {
            roleCodes.add("user");
        }
        return roleCodes;
    }

    private boolean verifyPassword(SysUser user, String rawPassword) {
        if (user == null) {
            BCrypt.verifyer().verify(rawPassword.toCharArray(),
                    "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidi");
            return false;
        }
        BCrypt.Result result = BCrypt.verifyer()
                .verify(rawPassword.toCharArray(), user.password);
        return result.verified;
    }
}
