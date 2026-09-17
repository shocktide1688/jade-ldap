package com.jade.admin.service;

import com.jade.admin.entity.SysConfig;
import com.jade.admin.repository.SysConfigRepository;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * 登录验证码服务
 *
 * 设计要点：
 *  - SVG 纯字符串生成，不依赖 AWT（GraalVM native image 安全）
 *  - 答案存 Redis，TTL 120s，一次性（校验即删）
 *  - 失败计数按用户名维度，TTL 15min，成功登录清零
 *  - 开关/阈值来自 sys_config（sys.captcha.enabled / sys.captcha.threshold），
 *    每次登录实时读取，后台改配置立即生效
 */
@ApplicationScoped
public class CaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final String FAIL_KEY_PREFIX = "login:fail:";
    private static final Duration CAPTCHA_TTL = Duration.ofSeconds(120);
    private static final Duration FAIL_TTL = Duration.ofMinutes(15);

    /** 去掉易混淆字符（0O1lI） */
    private static final String CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz";

    private static final String[] PALETTE = {
            "#00a86b", "#22d3ee", "#8b5cf6", "#f59e0b", "#ec4899", "#38bdf8",
    };

    private final SecureRandom random = new SecureRandom();

    @Inject
    RedisDataSource redis;

    @Inject
    SysConfigRepository configRepository;

    private ValueCommands<String, String> strCmd() {
        return redis.value(String.class, String.class);
    }

    // ---------- 配置 ----------

    public boolean isEnabled() {
        SysConfig c = configRepository.find("configKey = ?1 and deleted = false", "sys.captcha.enabled")
                .firstResult();
        return c != null && "true".equalsIgnoreCase(c.configValue);
    }

    public int threshold() {
        SysConfig c = configRepository.find("configKey = ?1 and deleted = false", "sys.captcha.threshold")
                .firstResult();
        if (c == null) return 3;
        try {
            int n = Integer.parseInt(c.configValue.trim());
            return Math.max(1, n);
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    /** 该用户名本次登录是否需要验证码：开关开启 且 失败次数达到阈值 */
    public boolean isRequired(String username) {
        return isEnabled() && failCount(username) >= threshold();
    }

    // ---------- 验证码生成 / 校验 ----------

    public record CaptchaImage(String captchaId, String svg) {}

    public CaptchaImage generate() {
        String code = randomCode(4);
        String id = UUID.randomUUID().toString().replace("-", "");
        strCmd().set(CAPTCHA_KEY_PREFIX + id, code.toLowerCase(), new SetArgs().ex(CAPTCHA_TTL));
        return new CaptchaImage(id, toSvg(code));
    }

    /** 校验并消费（一次性）；id 或 code 为空视为失败 */
    public boolean verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            return false;
        }
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String expected = strCmd().get(key);
        // 先删后比，无论对错都作废，防重放
        redis.key().del(key);
        return expected != null && expected.equalsIgnoreCase(captchaCode.trim());
    }

    // ---------- 登录失败计数 ----------

    public int failCount(String username) {
        String v = strCmd().get(FAIL_KEY_PREFIX + username);
        if (v == null) return 0;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void recordFail(String username) {
        if (username == null || username.isBlank()) return;
        String key = FAIL_KEY_PREFIX + username;
        long n = strCmd().incr(key);
        if (n == 1) {
            redis.key().expire(key, FAIL_TTL.toSeconds());
        }
    }

    public void resetFail(String username) {
        if (username == null || username.isBlank()) return;
        redis.key().del(FAIL_KEY_PREFIX + username);
    }

    // ---------- 私有 ----------

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * SVG 验证码：逐字符随机颜色/旋转 + 干扰线 + 噪点圆。
     * 浏览器端渲染，服务端零图像依赖。
     */
    private String toSvg(String code) {
        int width = 120;
        int height = 40;
        StringBuilder sb = new StringBuilder(512);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">");

        // 干扰线
        for (int i = 0; i < 4; i++) {
            sb.append("<line x1=\"").append(rand(width)).append("\" y1=\"").append(rand(height))
                    .append("\" x2=\"").append(rand(width)).append("\" y2=\"").append(rand(height))
                    .append("\" stroke=\"").append(randColor()).append("\" stroke-width=\"1\" stroke-opacity=\"0.4\"/>");
        }
        // 噪点圆
        for (int i = 0; i < 8; i++) {
            sb.append("<circle cx=\"").append(rand(width)).append("\" cy=\"").append(rand(height))
                    .append("\" r=\"").append(1 + random.nextInt(2))
                    .append("\" fill=\"").append(randColor()).append("\" fill-opacity=\"0.35\"/>");
        }
        // 字符：随机轻微旋转与纵向抖动
        int charWidth = width / (code.length() + 1);
        for (int i = 0; i < code.length(); i++) {
            int x = charWidth * (i + 1);
            int y = height / 2 + 6 + random.nextInt(7) - 3;
            int rotate = random.nextInt(24) - 12;
            sb.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" font-family=\"monospace\" font-size=\"24\" font-weight=\"bold\" fill=\"")
                    .append(randColor())
                    .append("\" text-anchor=\"middle\" transform=\"rotate(").append(rotate).append(' ')
                    .append(x).append(' ').append(y).append(")\">")
                    .append(code.charAt(i)).append("</text>");
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private int rand(int bound) {
        return random.nextInt(bound);
    }

    private String randColor() {
        return PALETTE[random.nextInt(PALETTE.length)];
    }
}
