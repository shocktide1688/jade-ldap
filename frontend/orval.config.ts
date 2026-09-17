import { defineConfig } from 'orval'

/**
 * Orval 配置：从后端 OpenAPI 自动生成 TS 客户端 + Vue Query hooks
 *
 * 使用：
 *   后端启动后 → npm run gen:api
 *   监听模式 → npm run gen:api:watch
 */
export default defineConfig({
  jade: {
    input: {
      target: 'http://localhost:8080/q/openapi',
    },
    output: {
      mode: 'tags-split',
      target: 'src/api/generated/endpoints',
      schemas: 'src/api/generated/model',
      fileExtension: '.gen.ts',
      client: 'axios',
      clean: true,
      prettier: true,
    },
    hooks: {
      afterAllFilesWrite: 'prettier --write',
    },
  },
})
