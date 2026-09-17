package com.jade.admin.repository;

import com.jade.admin.entity.SysLoginLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SysLoginLogRepository implements PanacheRepository<SysLoginLog> {
}
