package com.jade.admin.repository;

import com.jade.admin.entity.SysOss;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SysOssRepository implements PanacheRepository<SysOss> {
}
