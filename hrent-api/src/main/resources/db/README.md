# 数据库迁移与种子数据（hrent）

> 对应文档：`docs/00`（表设计来源）、`docs/03`（迁移与对账要求）、`docs/13`（接口字段契约）
> 技术：MySQL 8.0 + Flyway。**本目录是数据库的唯一事实来源，禁止再手工执行 `sql/house_rent.sql` 改库。**

---

## 一、目录结构

```
src/main/resources/db/
├── migration/                    # Flyway 版本化脚本，按版本号顺序执行，执行后不可修改
│   ├── V1__baseline_legacy_schema.sql     存量 13 张表（只 CREATE IF NOT EXISTS，不 DROP）
│   ├── V2__normalize_money_lease_role.sql 金额/日期/租约状态/多角色/密码算法
│   ├── V3__rating_module.sql              平台核验 + 评价 + 申诉 + 评分汇总
│   ├── V4__repair_module.sql              报修工单 + 图片 + 流转日志
│   ├── V5__bill_module.sql                账单（扩展 hrent_rent）+ 明细
│   └── V6__ai_module.sql                  向量表（二期）+ AI 埋点（本期用）
├── seed/
│   ├── seed_dev.sql             开发/演示种子：60 房 / 41 租约 / 50 评价 / 90 账单
│   └── reset_dev.sql            只删 SEED 前缀数据，不动表结构
├── rollback/
│   └── rollback_all.sql         仅演示环境用：撤掉 V2~V6（会 DROP，慎重）
├── verify.sql                   对账脚本，20 项，可随时重跑
└── README.md
```

**种子数据 ID 约定**（全部可用前缀一键清理）：

| 对象 | 前缀 | 数量 |
|---|---|---|
| 房东 / 租客 | `SEEDL1..3` / `SEEDT1..6` | 3 / 6 |
| 房源 | `SEEDH0001..0060` | 60 |
| 租约 | `SEEDG001..041` | 41（含 1 条超窗负样本） |
| 账单 | `SEEDR001-1..030-3` | 90 |
| 账单明细 | `SEEDR###-#-1..4` | 360 |
| 评价 | `SEEDVH/VL001..025` | 50 |
| 核验单 | `SEEDA001..060` | 60 |

---

## 二、两种使用路径

### 路径 A：全新库（本地开发 / 新同学入职）

```bash
# 1) 建库（排序规则必须与存量库一致，否则 JOIN 会报 Illegal mix of collations）
mysql -uroot -p -e "CREATE DATABASE house_rent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"

# 2) 启动应用，Flyway 自动跑 V1~V6
mvn spring-boot:run

# 3) 灌种子数据（不进 Flyway，手动执行）
mysql -uroot -p --default-character-set=utf8mb4 house_rent < src/main/resources/db/seed/seed_dev.sql

# 4) 对账
mysql -uroot -p --default-character-set=utf8mb4 house_rent < src/main/resources/db/verify.sql
```

### 路径 B：老库升级（已有 13 张表和数据）

```bash
# 0) 先备份，没有备份不许往下走
mysqldump -uroot -p --single-transaction --routines --triggers house_rent > backup_$(date +%Y%m%d_%H%M).sql

# 1) 只读预检：确认是 13 表干净老库、无 V2/V5 列、无空串日期
mysql -uroot -p --default-character-set=utf8mb4 house_rent < src/main/resources/db/preflight.sql

# 2) 启动应用，Flyway 自动：baseline 0 → 执行 V1（全部 IF NOT EXISTS，空操作）→ V2~V6
mvn spring-boot:run

# 3) 对账（重点看 2.x / 3.x / 5.x 是否全 0）
mysql -uroot -p --default-character-set=utf8mb4 house_rent < src/main/resources/db/verify.sql
```

> **为什么 V1 能安全跑在老库上**：13 张表全部 `CREATE TABLE IF NOT EXISTS`，命中即跳过。
> **为什么需要 baseline**：老库没有 `flyway_schema_history` 表，Flyway 见到非空库会拒绝执行，必须 `baseline-on-migrate=true`。

---

## 三、依赖与配置

先加**一个**依赖（Boot 2.5.5 管理的是 Flyway **7.7.3**，MySQL 支持在 core 里）：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

> **不要加 `flyway-mysql`**：该制品 8.2.0 才从 core 拆出，7.7.3 没有；照旧文档抄会直接构建失败。
> 将来升到 Boot 2.7.18（Flyway 8.5.x）时，才需要补 `flyway-mysql`。

然后加到 `application.yml`：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true        # 老库首次接入必需
    baseline-version: 0              # 从 0 起算，保证 V1 也会被执行（幂等）
    baseline-description: legacy 13 tables
    validate-on-migrate: true
    out-of-order: false              # 禁止乱序补脚本
  datasource:
    url: jdbc:mysql://localhost:3306/house_rent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
```

**时区三处必须一致**，缺一个就差 8 小时：
1. JDBC：`serverTimezone=Asia/Shanghai`
2. 容器：`TZ=Asia/Shanghai`
3. MySQL：`default-time-zone='+08:00'`（或保持 SYSTEM 且宿主机为东八区）

---

## 四、对账脚本怎么读

`verify.sql` 每行输出 `检查项 / 实际 / 期望`，共 9 组 20 项。**只要有一项"实际 ≠ 期望"，不许进联调。**

| 组 | 查什么 | 不符意味着 |
|---|---|---|
| 1 | 表数 25、6 个关键字段是否存在 | 迁移没跑完 / 跑错库 |
| 2 | 金额：`ROUND(money*100) = money_fen` | 迁移丢精度，会错账 |
| 3 | 账单明细之和 = 应收 | 账单页金额对不上 |
| 4 | 评分汇总 = 明细聚合、分数不越界 | 列表排序错乱 |
| 5 | 评价只挂在"已退租 + 30 天内"的租约 | 防刷逻辑被绕过 |
| 6 | 租约状态分布（种子期望 10/5/26） | 种子数据不对 |
| 7 | 全库 utf8mb4 且排序规则唯一 | JOIN 报 collation 错 |
| 8 | 5 个关键索引存在 | 列表页会慢查询 |
| 9 | 会话时区 | 日期差 8 小时 |

---

## 五、设计要点（为什么这么建）

| 决策 | 原因 |
|---|---|
| 金额双写 `money`(元) + `money_fen`(分) | 老代码读 `money`，新代码读 `money_fen`，两边都不用改就能跑；一次性迁移，不污染 74 个 Java 文件 |
| 租约加 `status` + `actual_end_date` | 原表只有字符串 `end_time`，系统不知道租约何时结束，"结束后 30 天可评"永远触发不了 |
| 账单扩展 `hrent_rent` 而非新建 bill 表 | 该表已是"租约 + 应缴 + 实缴 + 状态"的账单雏形，新建会导致"在租管理"页和账单页对不上账 |
| 老字段 `state` 保留，新增 `bill_status` | 老 Controller 读 `state`，新接口读 `bill_status`，互不干扰 |
| 评价带 `lease_id` 且有唯一索引 | 唯一索引是"同一租约只能评一次"的最后一道防线，不能只靠代码判断 |
| 用 CHECK 表做迁移断言，不用存储过程 | Flyway 对 `DELIMITER` 支持不稳，存储过程会让迁移整体失败；CHECK 表更可靠（需 8.0.16+） |
| 新表全部 `utf8mb4_general_ci` | 与存量库一致；混用 `0900_ai_ci` 会在 JOIN 时报错 |

---

## 六、红线

1. **已执行的 V 脚本不许改**。改了会导致 `validate` 失败，且各环境 schema 分叉。要改就加 `V7__fix_xxx.sql`。
2. **不许在 `migration/` 里写 DML 大批量数据**（种子、演示数据一律放 `seed/`）。
3. **不许 `DROP DATABASE` / `DROP TABLE` 存量表**。`sql/house_rent.sql` 第 12 行有 `DROP DATABASE`，那个脚本只用于"从零重建本地库"，不要拿它升级。
4. **迁移只做向前兼容**：加字段、加表、加索引可以；删字段、改类型不行（回滚靠代码回退，不靠库回退）。
5. **每个 PR 涉及表结构，必须同时更新 `verify.sql`**，否则对账有盲区。

---

## 七、常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| `Flyway found non-empty schema without schema history table` | 老库首次接入没开 baseline | 加 `baseline-on-migrate=true` |
| `Check constraint 'ck_xxx' is not enforced` | MySQL < 8.0.16 | 升到 8.0.16+（本项目目标 8.0） |
| 迁移脚本报 `DELIMITER` 语法错 | Flyway 不支持 DELIMITER | 不要写存储过程，用 CHECK 表断言（V2/V5 已按此实现） |
| `Illegal mix of collations` | 新旧表排序规则不同 | 新表统一 `utf8mb4_general_ci`；已建错的执行 `ALTER TABLE x CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci` |
| 日期差 8 小时 | JDBC 没传 `serverTimezone` | 见第三节，三处时区同时对齐 |
| 种子跑第二遍报主键冲突 | 没用本目录的 `seed_dev.sql`（它开头有清理段） | 用 `reset_dev.sql` 清干净再灌 |
| 想推倒重来 | — | 演示环境执行 `rollback/rollback_all.sql`，再重跑迁移 |

---

## 八、谁负责

| 角色 | 职责 |
|---|---|
| BE | 维护 `migration/`，每个 PR 附 `verify.sql` 影响项；改表前先在本地跑一遍 |
| QA | W2 起每次提测前跑 `verify.sql`，把输出贴进测试报告；`2.x/3.x/5.x` 任一非 0 直接打回 |
| PM | W3 验收"迁移 + 回滚 + 对账"三件套是否齐（对应 00 文档 P0-5） |
