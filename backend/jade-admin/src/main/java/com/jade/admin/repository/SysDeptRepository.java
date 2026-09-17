package com.jade.admin.repository;

import com.jade.admin.entity.SysDept;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class SysDeptRepository implements PanacheRepository<SysDept> {

    public List<SysDept> listAllActive() {
        return list("deleted = false and status = 1 order by parentId, sortOrder");
    }

    public List<SysDept> listChildren(Long parentId) {
        return list("parentId = ?1 and deleted = false order by sortOrder", parentId);
    }
}
