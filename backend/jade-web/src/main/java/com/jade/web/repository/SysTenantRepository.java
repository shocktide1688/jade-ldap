package com.jade.web.repository;

import com.jade.web.entity.SysTenant;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class SysTenantRepository implements PanacheRepository<SysTenant> {

    public List<SysTenant> listAll() {
        return list("status = 1");
    }
}
