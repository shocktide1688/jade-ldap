package com.jade.admin.repository;

import com.jade.admin.entity.SysPatient;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SysPatientRepository implements PanacheRepository<SysPatient> {
}
