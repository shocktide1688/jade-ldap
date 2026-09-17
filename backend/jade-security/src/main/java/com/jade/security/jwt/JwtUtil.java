package com.jade.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 签发 / 解析工具
 *
 * 配置项（application.yml）：
 *   jade.jwt.secret: <64+ 字符>
 *   jade.jwt.expire-seconds: 7200
 *   jade.jwt.issuer: jade-platform
 */
@ApplicationScoped
public class JwtUtil {

    @ConfigProperty(name = "jade.jwt.secret")
    String secret;

    @ConfigProperty(name = "jade.jwt.expire-seconds", defaultValue = "7200")
    long expireSeconds;

    @ConfigProperty(name = "jade.jwt.issuer", defaultValue = "jade-platform")
    String issuer;

    private SecretKey getKey() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jade.jwt.secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /** 生成 access token */
    public String generate(Long userId, String username, Map<String, Object> extra) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claims(extra != null ? extra : Map.of())
                .issuedAt(now)
                .expiration(exp)
                .signWith(getKey())
                .compact();
    }

    /** 解析 token */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 解析 userId */
    public Long getUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }
}
