package com.jade.admin;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * 字典管理接口集成测试（类型/字典项 CRUD、唯一性、级联删除）
 *
 * 注意：本测试需要 PG + Redis 运行（docker-compose up -d）
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DictControllerTest {

    /** 每次运行唯一 key, 避免与库中存量数据冲突 */
    private static final String KEY = "test_dict_" + System.nanoTime();
    private static Long typeId;

    @BeforeAll
    static void login() {
        // 显式固定端口: 防止 RestAssured 端口注入失效时请求打到 8080 的其它服务
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = Integer.getInteger("quarkus.http.test-port", 8081);
        System.out.println("[DictTest] RestAssured port = " + RestAssured.port);
        AuthHelpers.authAll(AuthHelpers.loginAsAdmin());
    }

    @AfterAll
    static void cleanup() {
        // 级联清理本次创建的类型及其字典项
        if (typeId != null) {
            given().when().delete("/api/v1/dict/types/" + typeId).then().statusCode(200);
        }
        AuthHelpers.clearAuth();
    }

    private Long createItem(String label, String value, int status) {
        return ((Number) given()
                .contentType(ContentType.JSON)
                .body("""
                        {"dictType":"%s","dictLabel":"%s","dictValue":"%s","sortOrder":1,"status":%d}
                        """.formatted(KEY, label, value, status))
                .when().post("/api/v1/dict/data")
                .then().statusCode(200).extract().path("data.id")).longValue();
    }

    @Test
    @Order(1)
    void create_type_and_duplicate_rejected() {
        typeId = ((Number) given()
                .contentType(ContentType.JSON)
                .body("""
                        {"dictName":"测试字典","dictType":"%s","remark":"dict test"}
                        """.formatted(KEY))
                .when().post("/api/v1/dict/types")
                .then().statusCode(200).extract().path("data.id")).longValue();

        // 同 key 再建 → 4009
        given().contentType(ContentType.JSON).body("""
                {"dictName":"重复类型","dictType":"%s"}
                """.formatted(KEY))
                .when().post("/api/v1/dict/types")
                .then().statusCode(200).body("code", equalTo(4009));
    }

    @Test
    @Order(2)
    void update_type_and_key_change_cascades_to_data() {
        Long itemId = createItem("标签A", "A", 1);

        // 改名称/状态
        given().contentType(ContentType.JSON).body("""
                {"dictName":"测试字典改名","status":0}
                """)
                .when().put("/api/v1/dict/types/" + typeId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.dictName", equalTo("测试字典改名"))
                .body("data.status", equalTo(0));

        // 改 dictType key → 级联迁移字典项
        String newKey = KEY + "_v2";
        given().contentType(ContentType.JSON).body("""
                {"dictType":"%s"}
                """.formatted(newKey))
                .when().put("/api/v1/dict/types/" + typeId)
                .then().statusCode(200).body("code", equalTo(0));

        // 旧 key 查不到, 新 key (all=true 含停用项) 查得到
        given().when().get("/api/v1/dict/data?type=" + KEY + "&all=true")
                .then().statusCode(200).body("data", hasSize(0));
        given().when().get("/api/v1/dict/data?type=" + newKey + "&all=true")
                .then().statusCode(200).body("data", hasSize(1))
                .body("data[0].id", equalTo(itemId.intValue()));

        // key 迁回, 后续用例沿用原 KEY
        given().contentType(ContentType.JSON).body("""
                {"dictType":"%s"}
                """.formatted(KEY))
                .when().put("/api/v1/dict/types/" + typeId)
                .then().statusCode(200).body("code", equalTo(0));
    }

    @Test
    @Order(3)
    void data_crud_and_all_flag() {
        Long itemId = createItem("停用项", "OFF", 0);

        // 用例2 已留一个启用项: 默认列表只含启用项(1), all=true 含停用项(2)
        given().when().get("/api/v1/dict/data?type=" + KEY)
                .then().statusCode(200).body("data", hasSize(1));
        given().when().get("/api/v1/dict/data?type=" + KEY + "&all=true")
                .then().statusCode(200).body("data", hasSize(2));

        // PUT 修改
        given().contentType(ContentType.JSON).body("""
                {"dictLabel":"停用项改","sortOrder":9,"status":1}
                """)
                .when().put("/api/v1/dict/data/" + itemId)
                .then().statusCode(200).body("code", equalTo(0))
                .body("data.dictLabel", equalTo("停用项改"))
                .body("data.sortOrder", equalTo(9))
                .body("data.status", equalTo(1));

        // 删除后 all=true 也不可见(剩用例2 的启用项)
        given().when().delete("/api/v1/dict/data/" + itemId)
                .then().statusCode(200).body("code", equalTo(0));
        given().when().get("/api/v1/dict/data?type=" + KEY + "&all=true")
                .then().statusCode(200).body("data", hasSize(1));
    }

    @Test
    @Order(4)
    void delete_type_cascades_items() {
        createItem("级联项", "C1", 1);
        given().when().delete("/api/v1/dict/types/" + typeId)
                .then().statusCode(200).body("code", equalTo(0));
        typeId = null; // cleanup 不再重复删
        given().when().get("/api/v1/dict/data?type=" + KEY + "&all=true")
                .then().statusCode(200).body("data", hasSize(0));
        given().contentType(ContentType.JSON).body("""
                {"dictName":"级联复用key","dictType":"%s"}
                """.formatted(KEY))
                .when().post("/api/v1/dict/types")
                .then().statusCode(200).body("code", equalTo(0)); // 软删后 key 可复用
    }
}
