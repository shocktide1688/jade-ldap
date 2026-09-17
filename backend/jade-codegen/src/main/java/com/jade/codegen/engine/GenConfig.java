package com.jade.codegen.engine;

import lombok.Builder;
import lombok.Data;

/**
 * 代码生成器配置
 */
@Data
@Builder
public class GenConfig {
    /** DB JDBC URL */
    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    private String dbDriver;

    /** 输出根目录 */
    private String outputDir;

    /** Java 基础包名 */
    private String basePackage;

    /** 业务模块名（用作包名后缀） */
    @Builder.Default
    private String moduleName = "demo";

    /** 作者（生成到文件 header） */
    @Builder.Default
    private String author = "Jade Codegen";

    /** 主键策略：IDENTITY（兼容旧表）或 SNOWFLAKE（应用层生成） */
    @Builder.Default
    private String idStrategy = "IDENTITY";
}
