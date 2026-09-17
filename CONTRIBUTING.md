# 贡献指南

感谢你愿意为 Jade Platform 贡献代码。

## 行为准则

请友好、尊重、包容。我们不接受任何形式的骚扰或不当言行。

## 提 Issue

- **Bug 反馈**：先搜索是否已有相同 issue
- **功能建议**：描述清楚场景、痛点、期望方案
- **问题模板**：尽量提供复现步骤、报错信息、环境

## 提 MR

### 流程

1. Fork
2. 创建 feature 分支（`feature/xxx` / `fix/xxx` / `docs/xxx`）
3. 提交代码（commit message 遵循 Conventional Commits）
4. 推到你 fork
5. 提 MR 到 `main`
6. 等 CI 通过 + 至少 1 个 reviewer approve

### Commit 规范

```
feat: 新增用户管理接口
fix: 修复 JWT 解析在 JDK 17 下的兼容问题
docs: 完善 README 排错指南
refactor: 抽出统一响应包装
test: 给 AuthController 加集成测试
chore: 升级 Quarkus 到 3.15.7
```

### 代码规范

**后端**：
- 4 空格缩进，UTF-8
- 公共类必须写 JavaDoc
- 不要在 Service 层直接返回 Entity，用 DTO
- 业务异常用 `BizException`，不要 `RuntimeException`
- Controller 不写业务逻辑，只做参数校验和转发

**前端**：
- 2 空格缩进，无分号
- `<script setup lang="ts">` 必用
- API 调用必须走 Orval 生成的 hooks，不手写 axios
- 组件 props 用 `defineProps<{...}>()` 强类型

### 提 MR 前自检

```bash
make backend-build   # 后端编译通过
make backend-test    # 后端测试通过
cd frontend && npm run type-check   # 前端类型检查
cd frontend && npm run lint         # 前端 lint
```

## 发布

只有 maintainer 能发版：

```bash
# 1. 确认 CHANGELOG.md 已更新
# 2. 打 tag
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
# 3. GitHub Actions 自动发布到 Maven 仓库
```
