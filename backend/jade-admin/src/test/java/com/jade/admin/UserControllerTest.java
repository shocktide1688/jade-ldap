package com.jade.admin;

import com.jade.security.entity.SysUser;
import com.jade.security.repository.SysUserRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 用户管理端到端测试
 *
 * 验证 CRUD + 分页 + 状态切换 + 重置密码
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserControllerTest {

    static {
        RestAssured.defaultParser = Parser.JSON;
    }

    @Inject
    SysUserRepository userRepository;

    @org.junit.jupiter.api.BeforeAll
    static void setup() {
        // 登录拿 admin token
        AuthHelpers.authAll(AuthHelpers.loginAsAdmin());
    }

    @org.junit.jupiter.api.AfterAll
    static void after() {
        AuthHelpers.clearAuth();
    }

    static final String PWD = "test123456";

    @Test
    @Order(1)
    void list_users_paginated() {
        given()
            .when().get("/api/v1/users/page?page=1&size=50")
            .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.total", greaterThanOrEqualTo(1))
                .body("data.records.username", hasItem("admin"));
    }

    @Test
    @Order(2)
    void create_user_then_get_by_id() {
        // 用 timestamp 保证唯一
        String uname = "testuser_" + System.currentTimeMillis();
        String body = "{\"username\":\"" + uname + "\",\"password\":\"" + PWD + "\",\"nickname\":\"A\",\"email\":\"a@a.com\"}";
        Integer userId = given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/v1/users")
            .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.username", equalTo(uname))
                .body("data.id", notNullValue())
                .extract().path("data.id");

        // 详情
        given()
            .when().get("/api/v1/users/" + userId)
            .then()
                .statusCode(200)
                .body("data.username", equalTo(uname))
                .body("data.password", nullValue());
    }

    @Test
    @Order(3)
    void update_user() {
        // 创建测试用户
        String uname = "testuser_upd_" + System.currentTimeMillis();
        Integer userId = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + uname + "\",\"password\":\"" + PWD + "\"}")
            .when().post("/api/v1/users")
            .then().statusCode(200).extract().path("data.id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + uname + "\",\"nickname\":\"updated\",\"email\":\"a2@a.com\"}")
            .when().put("/api/v1/users/" + userId)
            .then()
                .statusCode(200)
                .body("data.nickname", equalTo("updated"));

        // 清理
        given().when().delete("/api/v1/users/" + userId).then().statusCode(200);
    }

    @Test
    @Order(4)
    void cannot_delete_admin() {
        SysUser admin = userRepository.findByUsername("admin").orElseThrow();
        // BizException 映射成 200 + R.fail
        given()
            .when().delete("/api/v1/users/" + admin.id)
            .then()
                .statusCode(200)
                .body("code", not(equalTo(0)))
                .body("message", containsString("不能删除超级管理员"));
    }

    @Test
    @Order(5)
    void reset_password() {
        String uname = "testuser_pwd_" + System.currentTimeMillis();
        Integer userId = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + uname + "\",\"password\":\"" + PWD + "\"}")
            .when().post("/api/v1/users")
            .then().statusCode(200).extract().path("data.id");

        given()
            .when().put("/api/v1/users/" + userId + "/reset-password?newPassword=" + PWD)
            .then().statusCode(200);

        // 清理
        given().when().delete("/api/v1/users/" + userId).then().statusCode(200);
    }

    @Test
    @Order(6)
    void change_status() {
        String uname = "testuser_st_" + System.currentTimeMillis();
        Integer userId = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + uname + "\",\"password\":\"" + PWD + "\"}")
            .when().post("/api/v1/users")
            .then().statusCode(200).extract().path("data.id");

        given()
            .when().put("/api/v1/users/" + userId + "/status?status=0")
            .then().statusCode(200);
        given()
            .when().put("/api/v1/users/" + userId + "/status?status=1")
            .then().statusCode(200);

        given().when().delete("/api/v1/users/" + userId).then().statusCode(200);
    }

    @Test
    @Order(7)
    void delete_user() {
        String uname = "testuser_del_" + System.currentTimeMillis();
        Integer userId = given()
            .contentType(ContentType.JSON)
            .body("{\"username\":\"" + uname + "\",\"password\":\"" + PWD + "\"}")
            .when().post("/api/v1/users")
            .then().statusCode(200).extract().path("data.id");

        given()
            .when().delete("/api/v1/users/" + userId)
            .then().statusCode(200);
    }

    @Test
    @Order(8)
    void create_user_duplicate_username() {
        String body = "{\"username\":\"admin\",\"password\":\"" + PWD + "\"}";
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post("/api/v1/users")
            .then()
                .statusCode(200)
                .body("code", not(equalTo(0)))
                .body("message", containsString("用户名已存在"));
    }
}
