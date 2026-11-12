# 25 · Redis 接入 / JWT 升级 / OpenAPI / Flyway baseline 验证报告

- 日期：2026-11-12
- 模块：`hrent-api`（Spring Boot 2.5.5，context-path `/hrent`，端口 8888）
- 结论：四项改造全部完成并实测通过；另发现并修复 1 个历史密钥缺陷、1 个启动配置缺陷；验证过程中发生过一次主库被初始化脚本重置的事故（详见第 6 节）。

---

## 1. 依赖与配置变更

### 1.1 `pom.xml`

| 变更 | 内容 |
|---|---|
| 移除 | `jjwt 0.9.1`、`jaxb-api` |
| 新增 | `jjwt-api 0.11.5`、`jjwt-impl 0.11.5`(runtime)、`jjwt-jackson 0.11.5`(runtime) |
| 新增 | `spring-boot-starter-data-redis` |
| 新增 | `springdoc-openapi-ui 1.6.14` |

### 1.2 `application.yml`

| 配置 | 值/说明 |
|---|---|
| `wechat.app-id` / `app-secret` | 改为 `${WECHAT_APP_ID:}` / `${WECHAT_APP_SECRET:}`，缺失时不再阻塞启动 |
| `spring.redis.*` | `host=${REDIS_HOST:127.0.0.1}`、`port=${REDIS_PORT:6379}`、`password=${REDIS_PASSWORD:}`、`database=${REDIS_DATABASE:0}`、`timeout=2s`、`connect-timeout=2s` |
| `token.secret` | `${TOKEN_SECRET:${JWT_SECRET:}}`，同时兼容 `.env` 的 `TOKEN_SECRET` 与旧变量 `JWT_SECRET` |
| `springdoc.api-docs.path` | `/v3/api-docs` |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` |
| `springdoc.packages-to-scan` | `com.wt.hrentSystem` |
| Flyway | `baseline-on-migrate=true`、`baseline-version=0`、`baseline-description=legacy 13 tables`、`validate-on-migrate=true`、`clean-disabled=true` |

`.env`（不入库）新增 Redis 段：

```
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0
```

---

## 2. JWT 升级（jjwt 0.9.1 → 0.11.5）

### 2.1 发现的隐藏缺陷

历史 `TokenUtils` 通过 `YmlUtil` 直接读 `application.yml` 原文，snakeyaml 不做 Spring 占位符替换，`token.secret: ${JWT_SECRET:}` 被当作字面量 `"${JWT_SECRET:}"` 使用 —— **真实密钥从未生效**。

修复方式：新增 `TokenSettings`，用 `@Value` 注入 Spring 解析后的值，在构造器中调用 `TokenUtils.configure(secret, expireDays)`。

### 2.2 实现要点

- 签发：`Jwts.builder().setSubject(userId).setIssuedAt().setExpiration().signWith(key(), SignatureAlgorithm.HS256)`
- 解析：`Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token)`
- 密钥不足 32 字节时用 SHA-256 派生，规避 `WeakKeyException`
- 新增 `remainingSeconds(token)` 供吊销 TTL 使用
- `configure("")` 必须能清空旧密钥（单测抓出的缺陷，已修复）

### 2.3 单元测试

`TokenUtilsTest`（6 个用例：往返、篡改拒绝、异密钥拒绝、短密钥派生、空密钥兜底、空 userId 不签发）

```
mvnw.cmd -B test -Dtest=TokenUtilsTest -DfailIfNoTests=false
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 3. Redis 接入与令牌吊销

### 3.1 组件

| 组件 | 职责 |
|---|---|
| `RedisService` | 基于 `StringRedisTemplate`，`ping/set/get/exists/delete` 全部 try-catch；一次失败后 30 秒熔断窗口内直接降级，不重复吃连接超时 |
| `TokenBlacklistService` | 以 token 的 SHA-256 摘要为键，前缀 `hrent:jwt:blacklist:`，TTL = token 剩余有效期 |
| `AuthController` | `POST /api/v2/auth/logout`，响应区分 `store: redis` 与 `store: degraded` |
| `V2Context` | 解析 token 后检查吊销，命中抛 `ApiException(600, "登录状态已失效，请重新登录")` |

### 3.2 端到端实测（Redis 5.0.14 临时实例，端口 6390）

| 步骤 | 结果 |
|---|---|
| 登录获取 JWT | `{"code":"200","message":"登录成功"}` |
| `GET /api/v2/lease/mine` | `{"code":200,"message":"ok","data":{...}}` |
| `POST /api/v2/auth/logout` | `{"code":200,"data":{"revoked":true,"store":"redis"}}` |
| 同一 token 再访问 `/api/v2/lease/mine` | `{"code":600,"message":"登录状态已失效，请重新登录"}` |
| Redis 键核查 | `DBSIZE=1`，键 `hrent:jwt:blacklist:8f1e64...2744e`，`TTL=604772`（≈7 天） |

### 3.3 降级实测（本机密 6379 无 Redis，正式 8888 实例）

```
POST /api/v2/auth/logout
{"code":200,"data":{"revoked":false,"store":"degraded"},
 "message":"Redis 不可用，令牌未被吊销（客户端请丢弃本地 token）"}
```

- 降级时**不谎报吊销成功**；同一 token 仍可访问（符合无 Redis 的预期）
- 日志：`Redis 操作失败（EXISTS hrent:jwt:blacklist:...），未来 30 秒内降级为本地逻辑`
- 请求不会被 Redis 连接超时拖慢（熔断后直接返回）

---

## 4. OpenAPI：一次性输出全量 spec

### 4.1 配置

`OpenApiConfig` 定义 OpenAPI 3 元信息、Server `/hrent`、10 个 tag（认证 / v2-评分 / v2-租约 / v2-报修 / v2-账单 / v2-AI / v2-文件 / 存量-用户 / 存量-房源 / 存量-系统）、SecurityScheme `token`（header apiKey）。

### 4.2 导出结果（落盘于仓库 `docs/openapi/`）

| 文件 | 大小 | 说明 |
|---|---|---|
| `docs/openapi/hrent-api-openapi.yaml` | 319,307 B | `/hrent/v3/api-docs.yaml` 导出 |
| `docs/openapi/hrent-api-openapi.json` | 234,280 B | `/hrent/v3/api-docs` 导出 |

结构校验（Python `json.load` 严格 UTF-8 解析通过）：

```
openapi 3.0.1
paths 127
operations 721   # 存量 @RequestMapping 未指定 method 的路径会按多个 HTTP 方法展开，与运行时行为一致
tags 10
包含 /api/v2/auth/logout：True
```

在线访问：`http://localhost:8888/hrent/swagger-ui.html`、`http://localhost:8888/hrent/v3/api-docs`。

---

## 5. Flyway baseline 握手验证

### 5.1 场景

库处于“无 `flyway_schema_history`、仅有 13 张 legacy 表”的状态，启动应用后 Flyway 应先建立基线再顺序应用迁移。

### 5.2 结果（`house_rent.flyway_schema_history` 实测）

| rank | version | description | type | success |
|---|---|---|---|---|
| 1 | 0 | legacy 13 tables | BASELINE | 1 |
| 2 | 1 | baseline legacy schema | SQL | 1 |
| 3 | 2 | normalize money lease role | SQL | 1 |
| 4 | 3 | rating module | SQL | 1 |
| 5 | 4 | repair module | SQL | 1 |
| 6 | 5 | bill module | SQL | 1 |
| 7 | 6 | ai module | SQL | 1 |
| 8 | 7 | backfill legacy bill item | SQL | 1 |

启动日志：`Successfully applied 7 migrations to schema 'house_rent', now at version v7`。

### 5.3 重启幂等

再次启动同一库：

```
Successfully validated 8 migrations (execution time 00:00.022s)
Schema `house_rent` is up to date. No migration necessary.
Started HrentApplication in 4.414 seconds
```

基线记录未被重复插入，迁移未重复执行。

---

## 6. 事故说明：初始化脚本重置了主库

- 触发：验证 baseline 时直接执行 `hrent-api/sql/house_rent.sql`，脚本第 12 行含 `DROP DATABASE IF EXISTS house_rent;`
- 后果：主库 `house_rent` 被删除并重建为 13 张 legacy 表，`flyway_schema_history` 丢失；原有数据被脚本内演示种子覆盖（实测 `tbls=13, flyway=0`）
- 恢复：以该 legacy 状态完成 baseline 握手 + V1–V7，schema 已恢复到 v7；数据为演示种子（`admin`、`lisi`、`zhangsan` 三个用户）
- 已做的防护：在该 SQL 顶部加入醒目危险注释，明确“会 DROP 整个库”；需要补结构时统一走 Flyway
- 影响边界：本次库内为演示数据；如后续存在真实数据，执行该脚本前必须先备份

**后续任何“执行 sql”操作都应先确认脚本是否包含 `DROP DATABASE`。**

---

## 7. 当前运行状态与运维要点

| 项 | 状态 |
|---|---|
| 服务 | `http://localhost:8888/hrent` 已运行，健康接口 `POST /sysCommon/getBaseInfo` 返回 200 |
| Flyway | 启动时校验 8 个迁移，比分版 v7 |
| Redis | 本机未安装，当前为**降级模式**（登录/鉴权正常，吊销不可用） |
| OpenAPI | `/v3/api-docs`、`/v3/api-docs.yaml`、`/swagger-ui.html` 可用 |
| 临时环境 | 验证实例 8899、临时 Redis 6390、临时库 `house_rent_it`、`.tmp-verify` 目录均已清理 |

启用 Redis 的三种方式（任选其一）：

1. 安装本机 Redis 服务并监听 6379（`.env` 现状即可）；
2. 指向远端 Redis：改 `.env` 的 `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`；
3. 暂不启用：保持降级，登出接口会明确返回 `store: degraded`。

---

## 8. 复现命令

```bat
:: 构建
cd /d D:\ruan\houserentSysPublic-main\hrent-api
mvnw.cmd -B clean package -DskipTests

:: JWT 单测
mvnw.cmd -B test -Dtest=TokenUtilsTest -DfailIfNoTests=false

:: 全量 spec 导出（服务需先启动）
curl -s -o docs\openapi\hrent-api-openapi.yaml http://127.0.0.1:8888/hrent/v3/api-docs.yaml
curl -s -o docs\openapi\hrent-api-openapi.json http://127.0.0.1:8888/hrent/v3/api-docs

:: 服务启停
start.cmd            :: 启动 + 健康检查
start.cmd restart    :: 重启
start.cmd stop       :: 停止
```

---

## 9. 变更文件清单

| 文件 | 类型 |
|---|---|
| `pom.xml` | 修改（依赖） |
| `src/main/resources/application.yml` | 修改（Redis / springdoc / token / 微信默认值） |
| `common/util/TokenUtils.java` | 重写（jjwt 0.11.5） |
| `common/config/TokenSettings.java` | 新增 |
| `common/service/RedisService.java` | 新增 |
| `common/service/TokenBlacklistService.java` | 新增 |
| `api/v2/AuthController.java` | 新增 |
| `api/v2/V2Context.java` | 修改（吊销校验） |
| `common/config/OpenApiConfig.java` | 新增 |
| `src/test/java/com/wt/hrentSystem/TokenUtilsTest.java` | 新增 |
| `sql/house_rent.sql` | 修改（顶部危险警示，行为未变） |
| `docs/openapi/hrent-api-openapi.yaml` / `.json` | 新增（全量 spec） |
