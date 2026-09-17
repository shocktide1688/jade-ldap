package com.jade.admin.repository;

import com.jade.admin.entity.SysConfig;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SysConfigRepository implements PanacheRepository<SysConfig> {
}
