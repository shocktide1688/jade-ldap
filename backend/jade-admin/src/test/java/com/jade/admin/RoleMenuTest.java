package com.jade.admin;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 角色 + 菜单 RBAC 端到端测试
 *
 * 用 @BeforeAll 登录一次, 后续所有请求都带 admin token
 * @AfterAll 清理 RestAssured.requestSpecification, 不污染下一个 test class
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RoleMenuTest {

    static {
        RestAssured.defaultParser = Parser.JSON;
    }

    @BeforeAll
    static void login() {
        AuthHelpers.authAll(AuthHelpers.loginAsAdmin());
    }

    @AfterAll
    static void cleanup() {
        AuthHelpers.clearAuth();
    }

    @Test
    @Order(1)
    void list_all_roles() {
        given()
            .when().get("/api/v1/roles/all")
            .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data", hasSize(greaterThanOrEqualTo(3)))
                .body("data.roleCode", hasItems("admin", "user", "tenant"));
    }

    @Test
    @Order(2)
    void role_menu_assign_flow() {
        String uniqueCode = "test_role_" + System.currentTimeMillis();
        Response createRes = given()
            .contentType(ContentType.JSON)
            .body("{\"roleName\":\"测试角色\",\"roleCode\":\"" + uniqueCode + "\",\"dataScope\":\"ALL\",\"status\":1,\"roleSort\":99}")
            .when().post("/api/v1/roles");
        Integer roleId = createRes.path("data.id");

        given()
            .contentType(ContentType.JSON)
            .body("{\"menuIds\":[1, 10, 11]}")
            .when().put("/api/v1/roles/" + roleId + "/menus")
            .then().statusCode(200);

        given()
            .when().get("/api/v1/roles/" + roleId + "/menus")
            .then().statusCode(200)
                .body("data", hasSize(3));

        given().when().delete("/api/v1/roles/" + roleId).then().statusCode(200);
    }

    @Test
    @Order(3)
    void role_crud() {
        String uniqueCode = "crud_test_" + System.currentTimeMillis();
        Integer roleId = given()
            .contentType(ContentType.JSON)
            .body("{\"roleName\":\"CRUD测试\",\"roleCode\":\"" + uniqueCode + "\",\"dataScope\":\"SELF\",\"status\":1}")
            .when().post("/api/v1/roles")
            .then().statusCode(200).extract().path("data.id");

        given()
            .when().get("/api/v1/roles/" + roleId)
            .then().statusCode(200)
                .body("data.roleCode", equalTo(uniqueCode));

        given()
            .contentType(ContentType.JSON)
            .body("{\"dataScope\":\"DEPT\",\"status\":0}")
            .when().put("/api/v1/roles/" + roleId)
            .then().statusCode(200)
                .body("data.dataScope", equalTo("DEPT"))
                .body("data.status", equalTo(0));

        given().when().delete("/api/v1/roles/" + roleId).then().statusCode(200);
    }

    @Test
    @Order(4)
    void cannot_delete_admin_role() {
        Integer adminRoleId = given()
            .when().get("/api/v1/roles/all")
            .then().extract().path("data.find { it.roleCode == 'admin' }.id");

        given()
            .when().delete("/api/v1/roles/" + adminRoleId)
            .then()
                .statusCode(200)
                .body("code", not(equalTo(0)))
                .body("message", containsString("不能删除超级管理员角色"));
    }

    @Test
    @Order(5)
    void menu_tree_returns() {
        given()
            .when().get("/api/v1/menus/tree")
            .then()
                .statusCode(200)
                .body("data", hasSize(greaterThan(0)));
    }

    @Test
    @Order(6)
    void menu_router_returns_admin_full() {
        given()
            .when().get("/api/v1/menus/router")
            .then()
                .statusCode(200)
                .body("data", hasSize(greaterThan(0)))
                .body("data.name", hasItem("系统管理"));
    }
}
