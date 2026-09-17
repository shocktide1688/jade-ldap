package com.jade.codegen;

import com.jade.codegen.engine.CodeGenEngine;
import com.jade.codegen.engine.GenConfig;

/**
 * 代码生成器 CLI 入口
 *
 * 用法：
 *   1. 在 demo 项目 pom 引入：mvn exec:java -Dexec.mainClass="com.jade.codegen.Main"
 *   2. 或在 jade-codegen 模块直接跑
 *
 * 简化版：硬编码 demo 配置，业务可改
 */
public class Main {

    public static void main(String[] args) {
        GenConfig config = GenConfig.builder()
                .dbUrl("jdbc:postgresql://localhost:5432/jade")
                .dbUser("postgres")
                .dbPassword("postgres")
                .dbDriver("org.postgresql.Driver")
                .outputDir("./generated")
                .basePackage("com.example.app")
                .moduleName("user")
                .author("Kenneth")
                .build();

        CodeGenEngine engine = new CodeGenEngine(config);

        // 命令行参数：-t tableName 或 -all
        if (args.length == 0 || "-all".equals(args[0])) {
            engine.generateAll();
        } else if ("-t".equals(args[0]) && args.length > 1) {
            engine.generate(args[1]);
        } else {
            System.out.println("用法：");
            System.out.println("  java -jar jade-codegen.jar -all          生成所有表");
            System.out.println("  java -jar jade-codegen.jar -t tableName  生成指定表");
        }
    }
}
