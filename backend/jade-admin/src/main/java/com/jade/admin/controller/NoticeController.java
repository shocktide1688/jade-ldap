package com.jade.admin.controller;

import com.jade.admin.aspect.Log;
import com.jade.admin.entity.SysNotice;
import com.jade.admin.repository.SysNoticeRepository;
import com.jade.common.api.PageResult;
import com.jade.common.api.R;
import com.jade.security.entity.SysUser;
import com.jade.security.repository.SysUserRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/notices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "通知公告")
public class NoticeController {

    @Inject
    SysNoticeRepository noticeRepository;

    @Inject
    SysUserRepository userRepository;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/page")
    @Operation(summary = "分页查询公告")
    public R<PageResult<SysNotice>> page(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("title") String title) {
        String query = (title != null && !title.isBlank())
                ? "deleted = false and noticeTitle like ?1"
                : "deleted = false";
        Object[] params = (title != null && !title.isBlank())
                ? new Object[]{"%" + title + "%"}
                : new Object[]{};
        var q = noticeRepository.find(query, Sort.by("id").descending(), params);
        long total = q.count();
        List<SysNotice> records = q.page(Page.of(page - 1, size)).list();
        return R.ok(PageResult.of(records, total, page, size));
    }

    @GET
    @Path("/latest")
    @PermitAll
    @Operation(summary = "最新 5 条公告（首页用）")
    public R<List<SysNotice>> latest() {
        return R.ok(noticeRepository.find("deleted = false and status = 1",
                Sort.by("id").descending()).page(0, 5).list());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "公告详情")
    public R<SysNotice> getById(@PathParam("id") Long id) {
        SysNotice n = noticeRepository.findById(id);
        if (n == null) return R.fail(404, "公告不存在");
        return R.ok(n);
    }

    @POST
    @Transactional
    @Log(title = "通知公告", businessType = 1)
    @Operation(summary = "发布公告")
    public R<SysNotice> create(SysNotice notice) {
        notice.id = null;
        if (notice.noticeType == null) notice.noticeType = 1;
        if (notice.status == null) notice.status = 1;
        if (identity != null && !identity.isAnonymous() && identity.getPrincipal() != null) {
            notice.createdBy = identity.getPrincipal().getName();
        }
        noticeRepository.persist(notice);
        return R.ok(notice);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Log(title = "通知公告", businessType = 3)
    @Operation(summary = "删除公告")
    public R<Void> delete(@PathParam("id") Long id) {
        SysNotice n = noticeRepository.findById(id);
        if (n == null) return R.fail(404, "公告不存在");
        n.deleted = true;
        noticeRepository.persist(n);
        return R.ok();
    }
}
