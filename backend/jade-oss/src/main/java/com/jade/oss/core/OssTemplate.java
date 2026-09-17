package com.jade.oss.core;

import java.io.InputStream;

/**
 * 对象存储统一接口
 */
public interface OssTemplate {

    /** 上传文件，返回访问 URL */
    String upload(String key, InputStream input, long size, String contentType);

    /** 删除文件 */
    void delete(String key);

    /** 获取签名 URL（私有 bucket） */
    String getSignedUrl(String key, long expireSeconds);

    /** 桶名 */
    String getBucket();
}
