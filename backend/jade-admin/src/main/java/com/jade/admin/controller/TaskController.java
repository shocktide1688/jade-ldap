package com.jade.admin.controller;

import com.jade.admin.aspect.Log;
import com.jade.admin.entity.SysTask;
import com.jade.admin.repository.SysTaskRepository;
import com.jade.common.api.R;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "定时任务")
@RolesAllowed("admin")
public class TaskController {

    @Inject
    SysTaskRepository taskRepository;

    @GET
    @Operation(summary = "所有任务")
    public R<List<SysTask>> all() {
        return R.ok(taskRepository.list("deleted = false"));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "任务详情")
    public R<SysTask> getById(@PathParam("id") Long id) {
        SysTask t = taskRepository.findById(id);
        if (t == null) return R.fail(404, "任务不存在");
        return R.ok(t);
    }

    @POST
    @Transactional
    @Log(title = "定时任务", businessType = 1)
    @Operation(summary = "新建任务")
    public R<SysTask> create(SysTask task) {
        task.id = null;
        if (task.status == null) task.status = 1;
        if (task.concurrent == null) task.concurrent = 0;
        if (task.misfirePolicy == null) task.misfirePolicy = "3";
        if (task.taskGroup == null) task.taskGroup = "DEFAULT";
        taskRepository.persist(task);
        return R.ok(task);
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @Log(title = "定时任务", businessType = 2)
    @Operation(summary = "更新任务")
    public R<SysTask> update(@PathParam("id") Long id, SysTask req) {
        SysTask t = taskRepository.findById(id);
        if (t == null) return R.fail(404, "任务不存在");
        if (req.taskName != null) t.taskName = req.taskName;
        if (req.invokeTarget != null) t.invokeTarget = req.invokeTarget;
        if (req.cronExpression != null) t.cronExpression = req.cronExpression;
        if (req.concurrent != null) t.concurrent = req.concurrent;
        if (req.status != null) t.status = req.status;
        if (req.remark != null) t.remark = req.remark;
        taskRepository.persist(t);
        return R.ok(t);
    }

    @PUT
    @Path("/{id}/status")
    @Transactional
    @Log(title = "定时任务", businessType = 2)
    @Operation(summary = "启停任务")
    public R<Void> changeStatus(@PathParam("id") Long id, @QueryParam("status") Short status) {
        SysTask t = taskRepository.findById(id);
        if (t == null) return R.fail(404, "任务不存在");
        t.status = status;
        taskRepository.persist(t);
        return R.ok();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Log(title = "定时任务", businessType = 3)
    @Operation(summary = "删除任务")
    public R<Void> delete(@PathParam("id") Long id) {
        SysTask t = taskRepository.findById(id);
        if (t == null) return R.fail(404, "任务不存在");
        t.deleted = true;
        taskRepository.persist(t);
        return R.ok();
    }

    @PUT
    @Path("/{id}/run")
    @Log(title = "定时任务", businessType = 2)
    @Operation(summary = "立即执行一次（演示用）")
    public R<Void> runOnce(@PathParam("id") Long id) {
        SysTask t = taskRepository.findById(id);
        if (t == null) return R.fail(404, "任务不存在");
        // 真实场景用 Quartz 触发，这里仅记日志
        return R.ok();
    }
}
