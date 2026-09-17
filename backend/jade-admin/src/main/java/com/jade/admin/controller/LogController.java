package com.jade.admin.controller;

import com.jade.admin.entity.SysLoginLog;
import com.jade.admin.entity.SysOperLog;
import com.jade.admin.repository.SysLoginLogRepository;
import com.jade.admin.repository.SysOperLogRepository;
import com.jade.common.api.PageResult;
import com.jade.common.api.R;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/log")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "日志管理")
@RolesAllowed("admin")
public class LogController {

    @Inject
    SysOperLogRepository operLogRepository;

    @Inject
    SysLoginLogRepository loginLogRepository;

    // ---- 操作日志 ----

    @GET
    @Path("/oper/page")
    @Operation(summary = "分页查询操作日志")
    public R<PageResult<SysOperLog>> operPage(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("title") String title,
            @QueryParam("username") String username) {
        var q = operLogRepository.find(
                (title != null && !title.isBlank() ? "title like ?1 and " : "")
                + (username != null && !username.isBlank()
                    ? (title != null && !title.isBlank() ? "username like ?2" : "username like ?1")
                    : ""),
                Sort.by("operTime").descending(),
                title != null && !title.isBlank() && username != null && !username.isBlank()
                    ? new Object[]{"%" + title + "%", "%" + username + "%"}
                    : (title != null && !title.isBlank() ? new Object[]{"%" + title + "%"}
                        : (username != null && !username.isBlank() ? new Object[]{"%" + username + "%"}
                            : new Object[]{})));
        long total = q.count();
        List<SysOperLog> records = q.page(Page.of(page - 1, size)).list();
        return R.ok(PageResult.of(records, total, page, size));
    }

    @DELETE
    @Path("/oper/clean")
    @Transactional
    @Operation(summary = "清空操作日志")
    public R<Void> cleanOper() {
        operLogRepository.delete("1=1");
        return R.ok();
    }

    // ---- 登录日志 ----

    @GET
    @Path("/login/page")
    @Operation(summary = "分页查询登录日志")
    public R<PageResult<SysLoginLog>> loginPage(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("username") String username) {
        var q = loginLogRepository.find(
                username != null && !username.isBlank() ? "username like ?1" : "1=1",
                Sort.by("loginTime").descending(),
                username != null && !username.isBlank() ? new Object[]{"%" + username + "%"} : new Object[]{});
        long total = q.count();
        List<SysLoginLog> records = q.page(Page.of(page - 1, size)).list();
        return R.ok(PageResult.of(records, total, page, size));
    }

    @DELETE
    @Path("/login/clean")
    @Transactional
    @Operation(summary = "清空登录日志")
    public R<Void> cleanLogin() {
        loginLogRepository.delete("1=1");
        return R.ok();
    }
}
