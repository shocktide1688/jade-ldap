package com.jade.admin.service;

import com.jade.admin.entity.SysConfig;
import com.jade.admin.repository.SysConfigRepository;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 登录验证码服务
 *
 * 设计要点：
 *  - PNG 位图字形逐像素渲染，不依赖 AWT（GraalVM native image 安全）；
 *    答案不出现在任何标记里（SVG text 明文方案已废弃，机器人解码标记即可零成本识别）
 *  - 答案/失败计数存 Redis，TTL 到期，一次性（GETDEL 原子消费，并发不可复用）；
 *    进程内始终双写，Redis 不可用时自动降级为进程内（单机语义：重启归零、
 *    多实例各自计数），功能不中断；校验不过即拒绝，不放行
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

    /** 去掉易混淆字符（0O1I） */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final String[] PALETTE = {
            "#00a86b", "#22d3ee", "#8b5cf6", "#f59e0b", "#ec4899", "#38bdf8",
    };

    private static final Logger LOG = Logger.getLogger(CaptchaService.class);

    /** Redis 故障后熔断时长，到期重试 */
    private static final long REDIS_RETRY_MS = 30_000;

    private final SecureRandom random = new SecureRandom();
    private final InMemoryTtlStore memory = new InMemoryTtlStore();
    /** 0 = Redis 可用；否则为下次允许重试 Redis 的时间戳 */
    private volatile long redisRetryAt;

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

    public record CaptchaImage(String captchaId, String image) {}

    public CaptchaImage generate() {
        String code = randomCode(4);
        String id = UUID.randomUUID().toString().replace("-", "");
        String key = CAPTCHA_KEY_PREFIX + id;
        memory.setex(key, CAPTCHA_TTL.toSeconds(), code.toLowerCase());
        if (redisAvailable()) {
            try {
                strCmd().set(key, code.toLowerCase(), new SetArgs().ex(CAPTCHA_TTL));
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        return new CaptchaImage(id, toPngDataUri(code));
    }

    /** 校验并消费（一次性）；id 或 code 为空视为失败 */
    public boolean verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            return false;
        }
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String expected = null;
        if (redisAvailable()) {
            try {
                expected = strCmd().getdel(key); // GETDEL 原子消费, 并发不可复用
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        String mem = memory.getdel(key); // 进程内副本同步作废(含降级期兜底)
        if (expected == null) {
            expected = mem;
        }
        return expected != null && expected.equalsIgnoreCase(captchaCode.trim());
    }

    // ---------- 登录失败计数 ----------

    public int failCount(String username) {
        String key = FAIL_KEY_PREFIX + username;
        if (redisAvailable()) {
            try {
                String v = strCmd().get(key);
                return v == null ? 0 : Integer.parseInt(v);
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
        String v = memory.get(key);
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
        memory.increment(key, FAIL_TTL.toSeconds());
        if (redisAvailable()) {
            try {
                long n = strCmd().incr(key);
                if (n == 1) {
                    redis.key().expire(key, FAIL_TTL.toSeconds());
                }
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
    }

    public void resetFail(String username) {
        if (username == null || username.isBlank()) return;
        memory.del(FAIL_KEY_PREFIX + username);
        if (redisAvailable()) {
            try {
                redis.key().del(FAIL_KEY_PREFIX + username);
            } catch (Exception e) {
                markRedisDown(e);
            }
        }
    }

    // ---------- 私有 ----------

    private String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    // ---- 5x7 位图字体（行序自上而下，'1'=着色；仅覆盖 CODE_CHARS） ----
    private static final String FONT =
            "A=01110,10001,10001,11111,10001,10001,10001;B=11110,10001,10001,11110,10001,10001,11110;" +
            "C=01110,10001,10000,10000,10000,10001,01110;D=11110,10001,10001,10001,10001,10001,11110;" +
            "E=11111,10000,10000,11110,10000,10000,11111;F=11111,10000,10000,11110,10000,10000,10000;" +
            "G=01110,10001,10000,10111,10001,10001,01111;H=10001,10001,10001,11111,10001,10001,10001;" +
            "J=00111,00010,00010,00010,00010,10010,01100;K=10001,10010,10100,11000,10100,10010,10001;" +
            "L=10000,10000,10000,10000,10000,10000,11111;M=10001,11011,10101,10101,10001,10001,10001;" +
            "N=10001,11001,10101,10011,10001,10001,10001;P=11110,10001,10001,11110,10000,10000,10000;" +
            "Q=01110,10001,10001,10001,10101,10010,01101;R=11110,10001,10001,11110,10100,10010,10001;" +
            "S=01111,10000,10000,01110,00001,00001,11110;T=11111,00100,00100,00100,00100,00100,00100;" +
            "U=10001,10001,10001,10001,10001,10001,01110;V=10001,10001,10001,10001,10001,01010,00100;" +
            "W=10001,10001,10001,10101,10101,11011,10001;X=10001,10001,01010,00100,01010,10001,10001;" +
            "Y=10001,10001,01010,00100,00100,00100,00100;Z=11111,00001,00010,00100,01000,10000,11111;" +
            "2=01110,10001,00001,00010,00100,01000,11111;3=11111,00010,00100,00010,00001,10001,01110;" +
            "4=00010,00110,01010,10010,11111,00010,00010;5=11111,10000,11110,00001,00001,10001,01110;" +
            "6=00110,01000,10000,11110,10001,10001,01110;7=11111,00001,00010,00100,01000,01000,01000;" +
            "8=01110,10001,10001,01110,10001,10001,01110;9=01110,10001,10001,01111,00001,00010,01100";

    private static final java.util.Map<Character, boolean[][]> GLYPHS = new java.util.HashMap<>();
    static {
        for (String entry : FONT.split(";")) {
            int eq = entry.indexOf('=');
            char ch = entry.charAt(0);
            String[] rows = entry.substring(eq + 1).split(",");
            boolean[][] g = new boolean[7][5];
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    g[r][c] = rows[r].charAt(c) == '1';
                }
            }
            GLYPHS.put(ch, g);
        }
    }

    private static final int IMG_W = 120;
    private static final int IMG_H = 40;
    private static final int SCALE = 4;
    private static final int BG = 0xFFF2F4F7;

    /** 渲染验证码为 PNG data URI：位图字形 + 随机旋转 + 波形扰动 + 噪声 */
    private String toPngDataUri(String code) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] px = new int[IMG_W * IMG_H];
        java.util.Arrays.fill(px, BG);
        for (int i = 0; i < 3; i++) {
            drawLine(px, rnd.nextInt(IMG_W), rnd.nextInt(IMG_H), rnd.nextInt(IMG_W), rnd.nextInt(IMG_H),
                    rgb(rnd.nextInt(150, 210), rnd.nextInt(150, 210), rnd.nextInt(150, 210)));
        }
        int cellW = (IMG_W - 12) / code.length();
        for (int i = 0; i < code.length(); i++) {
            boolean[][] g = GLYPHS.get(code.charAt(i));
            if (g == null) continue;
            int gw = 5 * SCALE, gh = 7 * SCALE;
            int x0 = 6 + i * cellW + Math.max(0, (cellW - gw) / 2);
            int y0 = (IMG_H - gh) / 2;
            double angle = Math.toRadians(rnd.nextDouble(-18, 18));
            double sin = Math.sin(angle), cos = Math.cos(angle);
            int color = rgb(rnd.nextInt(20, 110), rnd.nextInt(20, 110), rnd.nextInt(20, 110));
            double waveAmp = 1.5 + rnd.nextDouble(1.5);
            double wavePhase = rnd.nextDouble(Math.PI * 2);
            int cx = x0 + gw / 2, cy = y0 + gh / 2;
            for (int py = Math.max(0, y0 - 6); py < Math.min(IMG_H, y0 + gh + 6); py++) {
                int shift = (int) Math.round(waveAmp * Math.sin(py / 5.0 + wavePhase));
                for (int pxx = Math.max(0, x0 - 6); pxx < Math.min(IMG_W, x0 + gw + 6); pxx++) {
                    int dx = pxx - cx, dy = py - cy;
                    int sx = (int) Math.round(dx * cos + dy * sin) + shift;
                    int sy = (int) Math.round(-dx * sin + dy * cos);
                    int gx = sx / SCALE + 2, gy = sy / SCALE + 3;
                    if (gx >= 0 && gx < 5 && gy >= 0 && gy < 7 && g[gy][gx]) {
                        px[py * IMG_W + pxx] = color;
                    }
                }
            }
        }
        for (int i = 0; i < 40; i++) {
            px[rnd.nextInt(IMG_H) * IMG_W + rnd.nextInt(IMG_W)] =
                    rgb(rnd.nextInt(100, 200), rnd.nextInt(100, 200), rnd.nextInt(100, 200));
        }
        return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(encodePng(px, IMG_W, IMG_H));
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawLine(int[] px, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            if (x1 >= 0 && x1 < IMG_W && y1 >= 0 && y1 < IMG_H) px[y1 * IMG_W + x1] = color;
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }

    /** 最小 PNG 编码（8-bit RGB，filter 0），零第三方依赖 */
    private static byte[] encodePng(int[] px, int w, int h) {
        byte[] raw = new byte[h * (w * 3 + 1)];
        int p = 0;
        for (int y = 0; y < h; y++) {
            raw[p++] = 0;
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                raw[p++] = (byte) (c >> 16);
                raw[p++] = (byte) (c >> 8);
                raw[p++] = (byte) c;
            }
        }
        Deflater def = new Deflater();
        try {
            def.setInput(raw);
            def.finish();
            byte[] idat = new byte[Math.max(64, raw.length)];
            int len = 0;
            while (!def.finished() && len < idat.length) {
                len += def.deflate(idat, len, idat.length - len);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 + len);
            byte[] sig = {(byte) 137, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            out.write(sig, 0, sig.length);
            writeChunk(out, "IHDR", ihdr(w, h));
            writeChunk(out, "IDAT", java.util.Arrays.copyOf(idat, len));
            writeChunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } finally {
            def.end();
        }
    }

    private static byte[] ihdr(int w, int h) {
        return new byte[]{
                (byte) (w >>> 24), (byte) (w >>> 16), (byte) (w >>> 8), (byte) w,
                (byte) (h >>> 24), (byte) (h >>> 16), (byte) (h >>> 8), (byte) h,
                8, 2, 0, 0, 0};
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        out.write((data.length >>> 24) & 0xFF);
        out.write((data.length >>> 16) & 0xFF);
        out.write((data.length >>> 8) & 0xFF);
        out.write(data.length & 0xFF);
        CRC32 crc = new CRC32();
        crc.update(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        crc.update(data);
        out.write(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, 4);
        out.write(data, 0, data.length);
        long v = crc.getValue();
        out.write((int) (v >>> 24) & 0xFF);
        out.write((int) (v >>> 16) & 0xFF);
        out.write((int) (v >>> 8) & 0xFF);
        out.write((int) v & 0xFF);
    }

    private boolean redisAvailable() {
        return System.currentTimeMillis() >= redisRetryAt;
    }

    private void markRedisDown(Exception e) {
        redisRetryAt = System.currentTimeMillis() + REDIS_RETRY_MS;
        LOG.warnf(e, "[Captcha] Redis 不可用，%ds 内降级为进程内存储", REDIS_RETRY_MS / 1000);
    }

    private int rand(int bound) {
        return random.nextInt(bound);
    }

    private String randColor() {
        return PALETTE[random.nextInt(PALETTE.length)];
    }
}
