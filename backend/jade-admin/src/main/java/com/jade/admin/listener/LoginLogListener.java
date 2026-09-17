package com.jade.admin.listener;

import com.jade.admin.entity.SysLoginLog;
import com.jade.admin.repository.SysLoginLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * 登录日志事件
 * 用 CDI event 解耦：AuthService 发布，listener 异步写库
 */
@ApplicationScoped
public class LoginLogListener {

    @Inject
    SysLoginLogRepository loginLogRepository;

    @Transactional
    public void onLogin(@Observes LoginEvent event) {
        SysLoginLog log = new SysLoginLog();
        log.username = event.username;
        log.ip = event.ip;
        log.status = (short) (event.success ? 1 : 0);
        log.msg = event.msg;
        log.loginTime = java.time.LocalDateTime.now();
        loginLogRepository.persist(log);
    }

    /** 登录事件 payload */
    public static class LoginEvent {
        public String username;
        public String ip;
        public boolean success;
        public String msg;

        public LoginEvent(String username, String ip, boolean success, String msg) {
            this.username = username;
            this.ip = ip;
            this.success = success;
            this.msg = msg;
        }
    }
}
