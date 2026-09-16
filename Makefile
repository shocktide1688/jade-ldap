# =====================================================
# Jade LDAP · 一键命令
# =====================================================
# 用法：make help

SHELL := /bin/zsh
# 强制用 JDK 21（覆盖 jenv 等已设置的 JAVA_HOME）
export JAVA_HOME := /Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
export PATH := $(JAVA_HOME)/bin:$(PATH)

DOCKER_COMPOSE = docker compose -f docker-compose.ldap.yml
BACKEND_DIR = backend
FRONTEND_DIR = frontend
LDAP_MODULE = jade-ldap

.DEFAULT_GOAL := help

.PHONY: help
help: ## 显示帮助
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# ---------------- 环境 ----------------
.PHONY: env
env: ## 检查环境（JDK / Maven / Node / Docker）
	@echo "JDK:    $$($(JAVA_HOME)/bin/java -version 2>&1 | head -1)"
	@echo "Maven:  $$(mvn -v 2>&1 | head -1)"
	@echo "Node:   $$(node -v)"
	@echo "Docker: $$(docker -v)"

# ---------------- 中间件 ----------------
.PHONY: up down restart logs
up: ## 启动 PG + Redis
	$(DOCKER_COMPOSE) up -d
	@echo "✅ PG + Redis 已启动"

down: ## 停止中间件
	$(DOCKER_COMPOSE) down
	@echo "🛑 已停止"

restart: down up ## 重启中间件

logs: ## 查看中间件日志
	$(DOCKER_COMPOSE) logs -f

# ---------------- 后端 ----------------
.PHONY: dev backend-build backend-package backend-test backend-clean
dev: up ## 启动后端 dev 模式（含热重载）
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" PATH="$(JAVA_HOME)/bin:$$PATH" mvn -B quarkus:dev -pl $(LDAP_MODULE) -am -Dquarkus.analytics.disabled=true

backend-build: ## 编译后端所有模块
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" mvn -B -DskipTests install

backend-package: ## 打包后端（JVM）
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" mvn -B -DskipTests -pl $(LDAP_MODULE) -am package

backend-native: ## 打包后端（GraalVM Native）
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" mvn -B -DskipTests -pl $(LDAP_MODULE) -am package -Dnative

backend-test: ## 跑后端测试
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" mvn -B -pl $(LDAP_MODULE) -am test

backend-clean: ## 清理后端构建产物
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" mvn -B clean

# ---------------- Docker ----------------
.PHONY: docker-build docker-run docker-native docker-native-run docker-full-up docker-full-down
docker-build: ## 构建后端 Docker 镜像（JVM）
	docker build -f Dockerfile.ldap -t jade-ldap:local .

docker-run: ## 运行后端 Docker 容器（JVM）
	$(DOCKER_COMPOSE) --profile full up -d jade-ldap

docker-full-up: ## 构建并启动完整容器栈
	$(DOCKER_COMPOSE) --profile full up -d --build

docker-full-down: ## 停止完整容器栈
	$(DOCKER_COMPOSE) --profile full down

docker-native: ## 构建 GraalVM Native Image（容器内构建，宿主机无需 GraalVM）
	cd $(BACKEND_DIR) && docker build -f jade-demo/Dockerfile.native -t jade-demo:native .

docker-native-run: ## 运行 Native Image（283MB，73ms 启动）
	docker run --rm -p 8080:8080 --network jade_default --name jade-demo \
		-e DB_URL=jdbc:postgresql://jade-postgres:5432/jade \
		-e DB_USERNAME=postgres \
		-e DB_PASSWORD=postgres \
		-e REDIS_URL=redis://jade-redis:6379 \
		-e JADE_JWT_SECRET=test-only-jwt-secret-min-32-bytes-required-please-ok \
		-e JADE_CRYPTO_MASTER_KEY=test-only-aes-256-bit-key-32-bytes-required-x \
		-e JADE_JWT_EXPIRE_SECONDS=7200 \
		-e JADE_JWT_ISSUER=jade-platform \
		jade-demo:native

# ---------------- 前端 ----------------
.PHONY: fe-install fe-dev fe-build fe-gen
fe-install: ## 安装前端依赖
	@cd $(FRONTEND_DIR) && npm install --legacy-peer-deps

fe-dev: ## 启动前端 dev server
	@cd $(FRONTEND_DIR) && npm run dev

fe-build: ## 构建前端生产包
	@cd $(FRONTEND_DIR) && npm run build

fe-gen: ## 重新生成 OpenAPI SDK
	@cd $(FRONTEND_DIR) && npm run gen:api

# ---------------- 全栈 ----------------
.PHONY: all-start all-stop
all-start: up fe-install ## 启动全栈（后端后台 + 前端后台）
	@echo "🚀 启动后端（用 JDK 21）..."
	@cd $(BACKEND_DIR) && JAVA_HOME="$(JAVA_HOME)" PATH="$(JAVA_HOME)/bin:$$PATH" nohup mvn -B quarkus:dev -pl $(LDAP_MODULE) -am -Dquarkus.analytics.disabled=true > /tmp/jade-ldap-backend.log 2>&1 &
	@echo "🚀 启动前端..."
	@cd $(FRONTEND_DIR) && nohup npm run dev > /tmp/jade-frontend.log 2>&1 &
	@sleep 15
	@echo "✅ 全栈已启动（看下面 log 确认）"
	@echo "  后端: http://localhost:8080"
	@echo "  前端: http://localhost:5173"
	@echo ""
	@echo "📋 验证："
	@echo "  tail -f /tmp/jade-ldap-backend.log   # 后端日志"
	@echo "  tail -f /tmp/jade-frontend.log  # 前端日志"

all-stop: ## 停止全栈
	@pkill -f "quarkus:dev" || true
	@pkill -f "vite" || true
	@$(DOCKER_COMPOSE) down
	@echo "🛑 全栈已停止"

# ---------------- 重置 ----------------
.PHONY: reset-db
reset-db: ## 重置数据库（清空 + 重新跑 Flyway）
	@echo "⚠️  即将清空数据库 jade"
	@docker exec -i jade-postgres psql -U postgres -c "DROP DATABASE IF EXISTS jade;"
	@docker exec -i jade-postgres psql -U postgres -c "CREATE DATABASE jade;"
	@echo "✅ 数据库已重置，下次启动时 Flyway 会自动建表"

# ---------------- 健康检查 ----------------
.PHONY: health login metrics grafana-import
health: ## 检查后端健康
	@curl -s http://localhost:8080/q/health | head -10

login: ## 用默认账号登录测试
	@curl -s -X POST http://localhost:8080/api/v1/auth/login \
		-H "Content-Type: application/json" \
		-d '{"username":"admin","password":"admin123"}' | head -3

metrics: ## 查看业务指标摘要
	@curl -s http://localhost:8080/api/v1/metrics/summary | python3 -m json.tool

grafana-import: ## 导入/更新 Grafana 业务仪表板
	@./monitoring/import-dashboard.sh
