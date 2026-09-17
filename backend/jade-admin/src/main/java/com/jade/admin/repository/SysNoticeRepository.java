package com.jade.admin.repository;

import com.jade.admin.entity.SysNotice;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SysNoticeRepository implements PanacheRepository<SysNotice> {
}
