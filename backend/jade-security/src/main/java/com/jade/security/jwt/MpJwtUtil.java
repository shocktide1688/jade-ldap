package com.jade.security.jwt;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Set;

/**
 * MP-JWT 兼容的 Token 生成器
 *
 * 为什么不用 jjwt：
 *   - jjwt 签的 token 与 SmallRye JWT 验证器不兼容
 *   - SmallRye JWT 严格遵循 MP-JWT 1.2 规范（iss/aud/exp/jti 必备）
 *   - jjwt 默认签的 token 不带这些标准 claim，会被验证器拒绝
 *
 * 用法：
 *   String token = mpJwtUtil.generate(userId, username, roles);
 *
 * 配置（application.yml）：
 *   mp.jwt.verify.issuer: jade-platform
 *   smallrye.jwt.sign.key.location: META-INF/jwt-secret.txt
 *   或者用 smallrye.jwt.sign.key 直接 inline
 */
@ApplicationScoped
public class MpJwtUtil {

    @ConfigProperty(name = "jade.jwt.issuer", defaultValue = "jade-platform")
    String issuer;

    @ConfigProperty(name = "jade.jwt.expire-seconds", defaultValue = "7200")
    long expireSeconds;

    /**
     * 生成 access token（RS256 签名）
     *
     * @param userId    用户 ID（放 sub）
     * @param username  用户名（放 upn + username claim）
     * @param roles     角色（放 groups，Quarkus @RolesAllowed 用这个）
     * @param tenantId  租户 ID（放 tenantId claim，给 TenantFilter 读）
     */
    public String generate(Long userId, String username, Set<String> roles, Long tenantId) {
        var builder = Jwt.issuer(issuer)
                .subject(String.valueOf(userId))
                .upn(username)
                .claim("username", username)
                .groups(roles == null ? Set.of("user") : roles);
        if (tenantId != null && tenantId > 0) {
            builder.claim("tenantId", tenantId);
        }
        return builder
                .expiresIn(Duration.ofSeconds(expireSeconds))
                .sign();
    }

    public String generate(Long userId, String username) {
        return generate(userId, username, Set.of("user"), null);
    }

    /**
     * 解析 token — 由调用方直接 @Inject JWTParser
     *
     * 已删除有 bug 的 parser() 方法（之前返回 null，会 NPE）
     */
}
