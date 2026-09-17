package com.jade.admin.repository;

import com.jade.admin.entity.SysDictData;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class SysDictDataRepository implements PanacheRepository<SysDictData> {

    public List<SysDictData> listByType(String dictType) {
        return list("dictType = ?1 and deleted = false and status = 1 order by sortOrder", dictType);
    }
}
