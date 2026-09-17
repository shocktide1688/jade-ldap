package com.jade.admin;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 多租户 + RBAC data-scope 端到端测试
 *
 * 场景 (来自 V8 seed):
 *   - admin    (tenant 1, dept 2 研发, role=admin  dataScope=ALL)
 *   - alice    (tenant 1, dept 2 研发, role=user   dataScope=SELF)
 *   - bob      (tenant 1, dept 2 研发, role=user   dataScope=SELF)
 *   - charlie  (tenant 1, dept 4 运营, role=user   dataScope=SELF)
 *   - user     (tenant 3, dept 4 运营, role=user   dataScope=SELF)
 *   - tenant   (tenant 3, dept 4 运营, role=tenant dataScope=DEPT)
 *   - dave     (tenant 3, dept 4 运营, role=tenant dataScope=DEPT)
 *
 * 预期:
 *   - admin     看全部 7 个
 *   - alice     只看自己 1 个 (SELF)
 *   - tenant    看 dept 4 的 4 个 (charlie/user/tenant/dave)
 *   - user      只看自己 1 个 (SELF)
 *   - dave      看 dept 4 的 4 个 (DEPT)
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataScopeE2ETest {

    static {
        RestAssured.defaultParser = Parser.JSON;
    }

    @BeforeAll
    static void setup() {
        AuthHelpers.clearAuth();
    }

    private static Response pageAs(String username, String password) {
        String token = AuthHelpers.login(username, password);
        return given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .when().get("/api/v1/users/page?page=1&size=50");
    }

    @Test
    @Order(1)
    void admin_sees_all_seed_users() {
        // 至少包含 7 个 seed user, total 可能 >= 7 (因为 UserControllerTest 可能先跑过留下 testuser)
        pageAs("admin", "admin123")
            .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.total", greaterThanOrEqualTo(7))
                .body("data.records.username", hasItems(
                        "admin", "alice", "bob", "charlie", "user", "tenant", "dave"));
    }

    @Test
    @Order(2)
    void alice_sees_only_herself() {
        // alice 在 dept 2, role=user, dataScope=SELF → 只看自己
        pageAs("alice", "admin123")
            .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.total", equalTo(1))
                .body("data.records[0].username", equalTo("alice"));
    }

    @Test
    @Order(3)
    void user_sees_only_herself() {
        // user (tenant 3) role=user, dataScope=SELF
        pageAs("user", "admin123")
            .then()
                .statusCode(200)
                .body("data.total", equalTo(1))
                .body("data.records[0].username", equalTo("user"));
    }

    @Test
    @Order(4)
    void tenant_sees_dept4_only() {
        // tenant (tenant 3) role=tenant, dataScope=DEPT → 看 dept 4 的所有用户 (4 个)
        // dept 4: charlie, user, tenant, dave
        pageAs("tenant", "admin123")
            .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("data.total", equalTo(4))
                .body("data.records.username", hasItems("charlie", "user", "tenant", "dave"))
                .body("data.records.username", not(hasItem("alice")))   // dept 2 不在范围内
                .body("data.records.username", not(hasItem("admin")));  // dept 2 不在范围内
    }

    @Test
    @Order(5)
    void dave_sees_dept4_only() {
        // dave (tenant 3) role=tenant, dataScope=DEPT → 跟 tenant 看到一样的 4 个
        pageAs("dave", "admin123")
            .then()
                .statusCode(200)
                .body("data.total", equalTo(4))
                .body("data.records.username", hasItems("charlie", "user", "tenant", "dave"))
                .body("data.records.username", not(hasItem("bob")));
    }

    @Test
    @Order(6)
    void charlie_sees_only_herself() {
        // charlie (tenant 1) role=user, dataScope=SELF
        pageAs("charlie", "admin123")
            .then()
                .statusCode(200)
                .body("data.total", equalTo(1))
                .body("data.records[0].username", equalTo("charlie"));
    }

    @Test
    @Order(7)
    void bob_sees_only_herself() {
        // bob (tenant 1) role=user, dataScope=SELF
        pageAs("bob", "admin123")
            .then()
                .statusCode(200)
                .body("data.total", equalTo(1))
                .body("data.records[0].username", equalTo("bob"));
    }

    @Test
    @Order(8)
    void keyword_search_respects_data_scope() {
        // tenant 角色搜 "user" → 应该只看到 dept 4 里 username 含 "user" 的
        // dept 4 里: charlie, user, tenant, dave → 只有 "user" 匹配
        String token = AuthHelpers.login("tenant", "admin123");
        given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .when().get("/api/v1/users/page?page=1&size=50&keyword=user")
            .then()
                .statusCode(200)
                .body("data.total", equalTo(1))
                .body("data.records[0].username", equalTo("user"));
    }
}
