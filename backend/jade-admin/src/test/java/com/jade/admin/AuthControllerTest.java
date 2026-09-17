package com.jade.admin;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * 认证接口集成测试
 *
 * 注意：本测试需要 PG + Redis 运行（docker-compose up -d）
 */
@QuarkusTest
class AuthControllerTest {

    @Test
    void login_success() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"admin","password":"admin123"}
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("code", equalTo(0))
                .body("message", equalTo("success"))
                .body("data.accessToken", notNullValue())
                .body("data.tokenType", equalTo("Bearer"))
                .body("data.expiresIn", equalTo(7200))
                .body("data.user.username", equalTo("admin"));
    }

    @Test
    void login_wrong_password_returns_business_error() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"admin","password":"wrong"}
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("code", equalTo(1001))
                .body("message", equalTo("用户名或密码错误"));
    }

    @Test
    void login_empty_username_validation_fails() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"","password":"admin123"}
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void me_without_token_returns_401() {
        given()
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }
}
