package com.jade.demo.controller;

import com.jade.common.api.R;
import com.jade.admin.entity.SysPatient;
import com.jade.admin.metrics.BusinessMetrics;
import com.jade.admin.repository.SysPatientRepository;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 病人信息接口（演示字段加密）
 */
@Path("/api/v1/patients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "病人信息")
@PermitAll
public class PatientController {

    @Inject
    SysPatientRepository repository;

    @Inject
    BusinessMetrics metrics;

    @POST
    @Transactional
    @Operation(summary = "创建病人（DB 自动加密敏感字段）")
    public R<SysPatient> create(SysPatient patient) {
        repository.persist(patient);
        metrics.recordPatientCreated();
        return R.ok(patient);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "查询病人（API 返回明文）")
    public R<SysPatient> get(@PathParam("id") Long id) {
        SysPatient p = repository.findById(id);
        if (p == null) return R.fail(404, "病人不存在");
        return R.ok(p);
    }
}
