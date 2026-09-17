package com.jade.admin.controller;

import com.jade.admin.entity.SysOss;
import com.jade.admin.repository.SysOssRepository;
import com.jade.common.api.PageResult;
import com.jade.common.api.R;
import com.jade.security.entity.SysUser;
import com.jade.security.repository.SysUserRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件存储接口（演示版本）
 *
 * 真实场景应该接 MinIO/OSS/S3，这里用本地存储 + DB 记录
 * 完整 OSS 模块在 jade-oss（已存在）
 */
@Path("/api/v1/oss")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "文件存储")
public class OssController {

    @Inject
    SysOssRepository ossRepository;

    @Inject
    SysUserRepository userRepository;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/page")
    @Operation(summary = "分页查询文件")
    public R<PageResult<SysOss>> page(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("name") String name) {
        var q = ossRepository.find(
                name != null && !name.isBlank() ? "deleted = false and originalName like ?1" : "deleted = false",
                Sort.by("uploadTime").descending(),
                name != null && !name.isBlank() ? new Object[]{"%" + name + "%"} : new Object[]{});
        long total = q.count();
        List<SysOss> records = q.page(Page.of(page - 1, size)).list();
        return R.ok(PageResult.of(records, total, page, size));
    }

    @POST
    @Path("/upload")
    @Transactional
    @Operation(summary = "记录文件上传（实际存储由 jade-oss 处理）")
    public R<SysOss> upload(SysOss req) {
        SysOss oss = new SysOss();
        oss.fileName = req.fileName;
        oss.originalName = req.originalName;
        oss.fileSuffix = req.fileSuffix;
        oss.fileSize = req.fileSize;
        oss.url = req.url;
        oss.storageType = req.storageType != null ? req.storageType : "LOCAL";
        oss.storagePath = req.storagePath;
        oss.bucket = req.bucket;
        oss.contentType = req.contentType;
        oss.uploadTime = LocalDateTime.now();
        if (identity != null && !identity.isAnonymous() && identity.getPrincipal() != null) {
            oss.uploadBy = identity.getPrincipal().getName();
        }
        ossRepository.persist(oss);
        return R.ok(oss);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @Operation(summary = "删除文件（软删）")
    public R<Void> delete(@PathParam("id") Long id) {
        SysOss oss = ossRepository.findById(id);
        if (oss == null) return R.fail(404, "文件不存在");
        oss.deleted = true;
        ossRepository.persist(oss);
        return R.ok();
    }
}
