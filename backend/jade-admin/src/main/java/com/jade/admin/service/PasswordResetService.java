package com.jade.admin.service;

import com.jade.admin.dto.ResetPasswordRequest;
import com.jade.common.constant.ResultCode;
import com.jade.common.exception.BizException;
import com.jade.security.entity.SysUser;
import com.jade.security.repository.SysUserRepository;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 忘记密码 · 邮箱验证码重置
 *
 * 流程：forgot-password 按邮箱发 6 位验证码（Redis 存 10 分钟、一次性，
 * 同邮箱 60s 冷却防轰炸）→ reset-password 校验验证码并 BCrypt 重置。
 * 出于防枚举考虑，forgot-password 对不存在的邮箱也返回成功（跳过发信）。
 */
@ApplicationScoped
public class PasswordResetService {

    private static final String CODE_KEY_PREFIX = "pwd-reset:";
    private static final String COOL_KEY_PREFIX = "pwd-reset-cool:";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration COOL_TTL = Duration.ofSeconds(60);

    private final SecureRandom random = new SecureRandom();

    @Inject
    RedisDataSource redis;

    @Inject
    MailService mailService;

    @Inject
    SysUserRepository userRepository;

    private ValueCommands<String, String> strCmd() {
        return redis.value(String.class, String.class);
    }

    /**
     * 发送重置验证码。邮箱不存在时不发信但同样返回成功（防枚举）。
     * 返回 Uni：发信失败（连接/认证错误）会以 BizException 结束。
     */
    public Uni<Void> sendResetCode(String email) {
        if (!mailService.isEnabled()) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "邮箱服务未开启，请联系管理员在「参数配置」中启用");
        }
        String coolKey = COOL_KEY_PREFIX + email;
        if (strCmd().get(coolKey) != null) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "发送过于频繁，请 1 分钟后再试");
        }
        strCmd().set(coolKey, "1", new io.quarkus.redis.datasource.value.SetArgs().ex(COOL_TTL.toSeconds()));

        String code = String.format("%06d", random.nextInt(1_000_000));
        strCmd().set(CODE_KEY_PREFIX + email, code, new io.quarkus.redis.datasource.value.SetArgs().ex(CODE_TTL.toSeconds()));

        SysUser user = userRepository.find("email = ?1 and deleted = false", email).firstResult();
        if (user == null) {
            return Uni.createFrom().voidItem(); // 静默跳过
        }
        return Uni.createFrom()
                .completionStage(mailService.sendResetCode(email, code).toCompletionStage())
                .onFailure().transform(e -> new BizException(
                        ResultCode.INTERNAL_ERROR, "邮件发送失败，请检查 SMTP 配置: " + e.getMessage()))
                .replaceWithVoid();
    }

    /** 校验验证码并重置密码，返回账号用户名（供前端回填登录） */
    public String reset(ResetPasswordRequest req) {
        String key = CODE_KEY_PREFIX + req.getEmail();
        String expected = strCmd().get(key);
        if (expected == null || !expected.equals(req.getCode().trim())) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "验证码错误或已过期");
        }
        redis.key().del(key); // 一次性

        SysUser user = userRepository.find("email = ?1 and deleted = false", req.getEmail()).firstResult();
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "该邮箱未绑定账号");
        }
        String hash = at.favre.lib.crypto.bcrypt.BCrypt.withDefaults()
                .hashToString(10, req.getNewPassword().toCharArray());
        if (userRepository.updatePassword(user.id, hash) != 1) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "密码重置失败");
        }
        return user.username;
    }
}
