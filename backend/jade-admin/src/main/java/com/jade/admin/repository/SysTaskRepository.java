package com.jade.admin.repository;

import com.jade.admin.entity.SysTask;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SysTaskRepository implements PanacheRepository<SysTask> {
}
