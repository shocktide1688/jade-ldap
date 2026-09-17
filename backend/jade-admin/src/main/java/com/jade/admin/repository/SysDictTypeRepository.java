package com.jade.admin.repository;

import com.jade.admin.entity.SysDictType;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SysDictTypeRepository implements PanacheRepository<SysDictType> {
}
