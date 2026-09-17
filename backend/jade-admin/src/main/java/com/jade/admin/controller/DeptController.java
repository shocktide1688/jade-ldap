package com.jade.admin.controller;

import com.jade.admin.aspect.Log;
import com.jade.admin.entity.SysDept;
import com.jade.admin.repository.SysDeptRepository;
import com.jade.common.api.R;
import com.jade.common.exception.BizException;
import com.jade.common.constant.ResultCode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/depts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "部门管理")
@RolesAllowed("admin")
public class DeptController {

    @Inject
    SysDeptRepository deptRepository;

    @GET
    @Path("/all")
    @Operation(summary = "所有启用的部门（下拉/树用）")
    public R<List<SysDept>> all() {
        return R.ok(deptRepository.listAllActive());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "查询部门")
    public R<SysDept> getById(@PathParam("id") Long id) {
        SysDept dept = deptRepository.findById(id);
        if (dept == null) return R.fail(404, "部门不存在");
        return R.ok(dept);
    }

    @POST
    @Transactional
    @Log(title = "部门管理", businessType = 1)
    @Operation(summary = "创建部门")
    public R<SysDept> create(SysDept dept) {
        if (dept.parentId == null) dept.parentId = 0L;
        if (dept.status == null) dept.status = 1;
        if (dept.sortOrder == null) dept.sortOrder = 0;
        dept.id = null;
        deptRepository.persist(dept);
        return R.ok(dept);
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Log(title = "部门管理", businessType = 2)
    @Operation(summary = "更新部门")
    public R<SysDept> update(@PathParam("id") Long id, SysDept req) {
        SysDept dept = deptRepository.findById(id);
        if (dept == null) return R.fail(404, "部门不存在");
        if (req.parentId != null) dept.parentId = req.parentId;
        if (req.deptName != null) dept.deptName = req.deptName;
        if (req.deptCode != null) dept.deptCode = req.deptCode;
        if (req.sortOrder != null) dept.sortOrder = req.sortOrder;
        if (req.leaderUserId != null) dept.leaderUserId = req.leaderUserId;
        if (req.phone != null) dept.phone = req.phone;
        if (req.email != null) dept.email = req.email;
        if (req.status != null) dept.status = req.status;
        deptRepository.persist(dept);
        return R.ok(dept);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Log(title = "部门管理", businessType = 3)
    @Operation(summary = "删除部门")
    public R<Void> delete(@PathParam("id") Long id) {
        SysDept dept = deptRepository.findById(id);
        if (dept == null) return R.fail(404, "部门不存在");
        if (!deptRepository.listChildren(id).isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "存在子部门，不能删除");
        }
        dept.deleted = true;
        deptRepository.persist(dept);
        return R.ok();
    }
}
