package com.jade.admin.service;

import com.jade.admin.entity.SysConfig;
import com.jade.admin.repository.SysConfigRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * 邮件服务：SMTP 配置存 sys_config（后台「参数配置」页可改，实时生效），
 * 通过 vert.x MailClient 发送 —— 不依赖 Jakarta Mail，GraalVM native 安全。
 */
@ApplicationScoped
public class MailService {

    @Inject
    SysConfigRepository configRepository;

    @Inject
    Vertx vertx;

    private String cfg(String key) {
        SysConfig c = configRepository.find("configKey = ?1 and deleted = false", "sys.mail." + key)
                .firstResult();
        return c == null ? null : c.configValue;
    }

    public boolean isEnabled() {
        return "true".equalsIgnoreCase(cfg("enabled"));
    }

    private String cfgOrDefault(String key, String def) {
        String v = cfg(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private MailConfig smtpConfig() {
        MailConfig mc = new MailConfig();
        mc.setHostname(cfgOrDefault("host", ""));
        mc.setPort(Integer.parseInt(cfgOrDefault("port", "465")));
        if ("true".equalsIgnoreCase(cfgOrDefault("ssl", "true"))) {
            mc.setSsl(true);
        } else {
            // 587 等 STARTTLS 端口自动升级；纯文本服务器也能兼容
            mc.setStarttls(StartTLSOptions.OPTIONAL);
        }
        String username = cfg("username");
        if (username != null && !username.isBlank()) {
            mc.setUsername(username);
            mc.setPassword(cfg("password"));
        }
        return mc;
    }

    /**
     * 发送密码重置验证码邮件。
     * 返回 vert.x Future 供上游（Uni）链接；配置缺失时同步抛 BizException。
     */
    public Future<Void> sendResetCode(String to, String code) {
        String from = cfg("from");
        String host = cfg("host");
        if (host == null || host.isBlank() || from == null || from.isBlank()) {
            throw new IllegalStateException("SMTP 配置不完整（host/from）");
        }
        MailMessage msg = new MailMessage()
                .setFrom(from)
                .setTo(to)
                .setSubject("Jade 平台 · 密码重置验证码")
                .setText("您正在重置登录密码，验证码：" + code + "（10 分钟内有效）。如非本人操作，请忽略本邮件。");
        MailClient client = MailClient.create(vertx, smtpConfig());
        return client.sendMail(msg)
                .onSuccess(v -> client.close())
                .onFailure(e -> client.close())
                .mapEmpty();
    }
}
