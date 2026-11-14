# 新表 DDL 核对 + 监控接入 + 时区/字符集固化（2026-11-14）

> 范围：`hrent-api`。三块交付：① rating / audit / review / repair_* / bill_item 的 DDL 齐备性核对；② Actuator + Prometheus 接入与 3 条告警；③ 字符集、排序规则、时区固化。
> 结论：**V9 迁移在“0900 默认排序规则”的脏库上也能跑通；健康与指标端点均 200；24 项对账全绿。**

## 一、新表 DDL 核对

这些表在 V3–V6 已建，本轮做逐项核对，并由 V9 增加“缺表即中断”的硬校验。

| 业务对象 | 实际表 | 迁移 | 主键 / 唯一键 | 关键索引 | 约束 |
|---|---|---|---|---|---|
| rating（评分汇总） | `rating_summary` | V3 | PK(target_type, target_id) | idx_display(target_type, display_score) | display_score 默认 4.00；贝叶斯平滑写入 |
| audit（核验单） | `house_audit` | V3 | PK(id) | idx_house_time(house_id, create_time) | 8 项核验权重之和 1.00，score 0.00~5.00 |
| review（评价流水） | `review` | V3 | PK(id)、UK(reviewer_id, target_type, target_id, lease_id) | idx_target、idx_lease | CHECK：score 1.0~5.0 / target_type / status |
| review 申诉 | `review_appeal` | V3 | PK(id)、UK(review_id, applicant_id) | idx_status(status, create_time) | CHECK：PENDING/ACCEPTED/REJECTED |
| repair 工单 | `repair_order` | V4 | PK(id)、UK(idem_key) | idx_tenant、idx_landlord、idx_house、idx_status | CHECK：状态机 6 态 / 类型 5 类 / 评分 1~5；version 乐观锁；idem_key 幂等 |
| repair 图片 | `repair_image` | V4 | PK(id) | idx_repair(repair_id, stage, sort) | CHECK：SUBMIT/COMPLETE |
| repair 流转日志 | `repair_log` | V4 | PK(id) | idx_repair(repair_id, create_time) | from_status 可空（首节点） |
| bill_item（账单明细） | `bill_item` | V5 | PK(id) | idx_rent(rent_id, sort) | CHECK：item_type 5 类 / amount_fen ≥ 0；SUM 必须等于主表应收 |

核对结论：

| 项 | 结果 |
|---|---|
| 8 张表是否齐备 | 齐备；V9 用 `8 - COUNT(*)` 做硬校验，缺一张迁移直接失败 |
| 是否都有主键与索引 | 有；verify 8.1 覆盖 5 个高频索引，实测缺失 0 |
| 字符集/排序规则 | 全部 `utf8mb4_general_ci`（V9 统一） |
| 金额字段 | 一律 `*_fen` BIGINT（分），与 W3 “金额分字段”口径一致 |

## 二、监控接入

### 2.1 依赖与出口

| 项 | 内容 |
|---|---|
| 依赖 | `spring-boot-starter-actuator`、`micrometer-registry-prometheus`（版本由 Boot 2.5.5 管理） |
| 健康 | `GET /hrent/actuator/health`（含 liveness/readiness 组，`show-details=always`） |
| 指标 | `GET /hrent/actuator/prometheus`（Prometheus 文本格式） |
| 调试 | `GET /hrent/actuator/metrics`、`/actuator/metrics/{name}` |
| 公共标签 | 所有指标带 `application="hrent-api"` |
| 请求指标 | `http.server.requests` 开启直方图与 SLO 桶（50ms/100ms/200ms/500ms/1s） |

实测（8899 实例，库 `house_rent_smk`，Redis 未部署）：

| 检查 | 结果 |
|---|---|
| `/hrent/actuator/health` | 200，`status=UP`；db UP、diskSpace UP、liveness/readiness UP |
| `/hrent/actuator/prometheus` | 200，含 `jvm_memory_used_bytes` |
| 连接池指标 | `hikaricp_connections_active{pool="hrent-pool"} 0.0`、`hikaricp_connections_max{pool="hrent-pool"} 10.0` |
| 请求指标 | `http_server_requests_seconds_count{method="GET",status="200",uri="/actuator/health"} 1.0` |

### 2.2 3 条告警

文件：`deploy/prometheus/alerts.yml`（抓取配置 `deploy/prometheus/prometheus.yml`）。

| # | 告警名 | 触发条件 | 持续 | 级别 | 说明 |
|---|---|---|---|---|---|
| 1 | `HrentApiDown` | `up{job="hrent-api"} == 0` | 1m | critical | 服务停止、端口不通、Actuator 不可达 |
| 2 | `HrentApiHigh5xxRate` | 5xx 请求占比 > 5%，且 5xx QPS > 0.1 | 5m | warning | 进程活着但接口在报错；带绝对量门槛，避免低流量下 1 个 5xx 就告警 |
| 3 | `HrentDbPoolSaturated` | `hikaricp_connections_active / hikaricp_connections_max > 0.9` | 5m | warning | 连接池接近打满（打满后 `connection-timeout=60s` 才 500） |

接入方式：

```bash
docker run -p 9090:9090 \
  -v $(pwd)/deploy/prometheus:/etc/prometheus \
  prom/prometheus:v2.45.0
# 抓取目标：host.docker.internal:8888 → /hrent/actuator/prometheus
```

### 2.3 接入时修掉的一个问题

`/actuator/health` 最初返回 **503 DOWN**：Redis 未部署时 `redis` 健康组件为 DOWN，导致整体 DOWN。Redis 在本项目是**可降级增强能力**（吊销名单/缓存，30s 熔断后业务继续跑），不应让探活失败。

修复：`management.health.redis.enabled: false`（DB 仍是硬依赖，DB DOWN 则整体 DOWN）。修复后 8888 生产实例三种探针均 200。

## 三、时区 / 字符集固化

### 3.1 发现的不一致

| 项 | 迁移前 | 问题 |
|---|---|---|
| 库默认排序规则 | `utf8mb4_0900_ai_ci`（MySQL 8 默认） | 业务表实际是 `utf8mb4_general_ci`；库/表不一致时手工建表会再冒 0900 表，跨规则 JOIN 直接 `Illegal mix of collations` |
| 会话时区 | `SYSTEM` | 取决于 MySQL 服务器/宿主机；库侧 `NOW()` 与 Java 侧可能差 8 小时 |

### 3.2 固化手段（四层）

| 层 | 配置 | 作用 |
|---|---|---|
| 库 | `V9__charset_timezone_hardening.sql`：`ALTER DATABASE ... COLLATE utf8mb4_general_ci` | 后续 `CREATE TABLE` 默认即 general_ci |
| 表 | V9 对 25 张业务表 `CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci` | 存量表与库统一；已一致时为空操作 |
| 连接 | `spring.datasource.hikari.connection-init-sql: SET time_zone='+08:00'` | 每条物理连接建立即固定会话时区，不依赖 `SYSTEM` |
| JVM | `scripts/hrent.ps1` / `start.sh` 加 `-Duser.timezone=Asia/Shanghai` | Java 侧时间解释/格式化固定北京时间；`spring.jackson.time-zone` 同步 |

V9 自带硬校验（不满足即中断迁移，不“看起来成功”）：

| 断言 | 判定 |
|---|---|
| B1 库默认排序规则 | `information_schema.schemata`，必须是 `utf8mb4_general_ci` |
| B2 业务表排序规则 | 非 `utf8mb4_general_ci` 的表数必须是 0 |
| B3 新表齐备 | 8 张新表缺一张即失败 |

说明：`@@collation_database` 在部分连接上不会随 `ALTER DATABASE` 刷新（实测 CLI 仍报 0900），因此 B1 与 `verify.sql` 的 10.3 都改用 `information_schema.schemata`。

## 四、验证结果

| 验证 | 库/环境 | 结果 |
|---|---|---|
| Flyway V1–V9（库创建时故意用 `utf8mb4_0900_ai_ci` 默认） | `house_rent_fw7` | 9 个版本全部 Success（V9 含 25 次 CONVERT） |
| `mvn test` | `house_rent_fw7` | 12/12 通过，BUILD SUCCESS |
| `verify.sql`（10 组 24 项） | `house_rent_fw7`（干净库） | 全部符合期望，含 10.1 缺表 0、10.2 非 general_ci 0、10.3 general_ci |
| 迁移后触发器 | `house_rent_fw7` / `house_rent_smk` / `house_rent` | 均 14 个（CONVERT 未丢触发器） |
| 生产库升级 | `house_rent`（V8→V9） | 版本 9、失败记录 0、触发器 14、默认排序规则 general_ci |
| 8888 生产实例 | 新 jar | `/actuator/health` 200、`/actuator/prometheus` 200、业务接口 200 |
| 告警 YAML | `prometheus.yml`、`alerts.yml` | YAML 解析通过（PyYAML） |

**一处数据层结论（非结构问题）**：`house_rent_smk` 上 verify 3.1/3.2 各报 1 条——该库在 V7（明细回填）之后又跑过老 `INSERT INTO hrent_rent`，老写入路径不会生成 `bill_item`，所以“有账单无明细”。干净迁移库为 0。意味着：**迁移后仍走老 SQL 写账单需要应用层补明细，或再加一个回填任务**，已记入待办。

## 五、变更清单

| 类型 | 文件 |
|---|---|
| 迁移 | `src/main/resources/db/migration/V9__charset_timezone_hardening.sql`（新增） |
| 依赖 | `pom.xml`：actuator + micrometer-registry-prometheus |
| 配置 | `application.yml`：actuator 暴露、指标标签、Hikari `connection-init-sql`、Jackson 时区、Redis 健康开关 |
| 启动 | `scripts/hrent.ps1`、`start.sh`：`-Duser.timezone=Asia/Shanghai` |
| 监控 | `deploy/prometheus/prometheus.yml`、`deploy/prometheus/alerts.yml`（新增） |
| 对账 | `verify.sql`：新增第 10 组 3 项，7.2 排除 flyway 历史表 |
| 文档 | `db/README.md` 同步 V9 与“10 组 24 项” |

## 六、遗留

| 级别 | 事项 |
|---|---|
| P1 | 老 SQL 写入 `hrent_rent` 不生成 `bill_item`，对账 3.1/3.2 会报“有账单无明细”；需应用层写入明细或补一条回填迁移 |
| P2 | `micrometer-registry-prometheus` 依赖的 `simpleclient_common` POM 在 Maven 侧报 invalid（仅告警，jar 可用、端点实测正常）；后续可显式锁定 prometheus client 版本 |
| P2 | `flyway_schema_history` 仍可能保持建库时的 0900 排序规则（V9 不碰 Flyway 自己的表，verify 已排除） |
| P2 | 3 条告警阈值为通用起步值，需按真实流量回归（尤其 5xx 比例与连接池使用率） |
| P2 | Windows 下 jar 被运行中的进程占用会导致 `mvn package` 失败（`Unable to rename ... .jar`），发布前必须先停服务 |
