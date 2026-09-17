package com.jade.codegen.engine;

import com.jade.codegen.db.JdbcSchemaReader;
import com.jade.codegen.db.SchemaReader;
import com.jade.codegen.model.TableMeta;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

/**
 * 代码生成器
 *
 * 用法：
 *   CodeGenEngine engine = new CodeGenEngine(config);
 *   engine.generate("sys_user");  // 生成单表
 *   engine.generateAll();          // 生成所有表
 */
@Slf4j
public class CodeGenEngine {

    private final GenConfig config;
    private final Configuration freemarker;
    private final SchemaReader reader;

    public CodeGenEngine(GenConfig config) {
        this.config = config;
        this.freemarker = initFreemarker();
        this.reader = initReader();
    }

    private Configuration initFreemarker() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        cfg.setClassLoaderForTemplateLoading(this.getClass().getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setNumberFormat("0.######");
        return cfg;
    }

    private SchemaReader initReader() {
        try {
            Class.forName(config.getDbDriver());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver not found: " + config.getDbDriver(), e);
        }
        try (Connection conn = DriverManager.getConnection(
                config.getDbUrl(), config.getDbUser(), config.getDbPassword())) {
            String dbType = config.getDbUrl().startsWith("jdbc:postgresql") ? "postgresql" : "mysql";
            return new JdbcSchemaReader(conn, dbType);
        } catch (Exception e) {
            throw new RuntimeException("Cannot connect to DB: " + e.getMessage(), e);
        }
    }

    public void generate(String tableName) {
        TableMeta table = reader.readTable(tableName);
        generateOne(table);
    }

    public void generateAll() {
        List<TableMeta> tables = reader.readAllTables();
        for (TableMeta table : tables) {
            try {
                generateOne(table);
            } catch (Exception e) {
                log.error("Failed to generate {}", table.getName(), e);
            }
        }
    }

    private void generateOne(TableMeta table) {
        String className = SchemaReader.toClassName(table.getName());
        log.info("Generating for table {} → {}", table.getName(), className);

        // 数据准备
        Map<String, Object> data = new HashMap<>();
        data.put("table", table);
        data.put("className", className);
        data.put("varName", uncapitalize(className));
        data.put("package", config.getBasePackage());
        data.put("module", config.getModuleName());
        data.put("author", config.getAuthor());
        data.put("idStrategy", config.getIdStrategy());
        data.put("date", new Date());
        data.put("columns", table.getColumns());
        table.getColumns().stream().filter(c -> c.isPrimaryKey()).findFirst()
                .ifPresent(c -> data.put("primaryKeyName", SchemaReader.toCamelCase(c.getName())));

        // 生成各类文件
        writeFile(data, "Entity.java.ftl",     getOutPath("entity", className + ".java"));
        writeFile(data, "Repository.java.ftl", getOutPath("repository", className + "Repository.java"));
        writeFile(data, "Service.java.ftl",    getOutPath("service", className + "Service.java"));
        writeFile(data, "Controller.java.ftl", getOutPath("controller", className + "Controller.java"));
        writeFile(data, "SearchDTO.java.ftl",  getOutPath("dto", className + "SearchDTO.java"));
    }

    private void writeFile(Map<String, Object> data, String template, String outPath) {
        try {
            Template t = freemarker.getTemplate(template);
            Path path = Paths.get(outPath);
            Files.createDirectories(path.getParent());

            try (Writer w = new OutputStreamWriter(
                    new FileOutputStream(outPath), StandardCharsets.UTF_8)) {
                t.process(data, w);
            }
            log.info("  → {}", outPath);
        } catch (TemplateException | IOException e) {
            log.error("Failed to write {}", outPath, e);
        }
    }

    private String getOutPath(String subDir, String fileName) {
        return String.format("%s/%s/%s/%s",
                config.getOutputDir(),
                subDir,
                config.getModuleName(),
                fileName);
    }

    private String uncapitalize(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
