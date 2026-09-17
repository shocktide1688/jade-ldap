package com.jade.admin.repository;

import com.jade.admin.entity.SysOperLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SysOperLogRepository implements PanacheRepository<SysOperLog> {
}
