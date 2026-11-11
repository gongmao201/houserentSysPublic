# 文档变更记录（CHANGELOG）

> 用途：给 `docs/` 下每份文档一个受控版本号，避免「标题写 v2.2、正文夹 v2.1/v2.3」这类口径漂移（见 `docs/14` C 类冲突）。
> 规则：
> 1. 任何文档改动都在本文件登记一行，写清版本、日期、改了什么、为什么。
> 2. 口径变更（数字、版本号、结论）必须同时更新引用它的文档；改一个字段要同步的 8 处见 `docs/17` §8。
> 3. `docs/00` 是 GATE 基线，只在 PM + BE 两人确认后升版。

## 一、当前版本总表

| 文档 | 版本 | 日期 | 状态 | 说明 |
|---|---|---|---|---|
| `00_主报告` | v2.2 | 2026-10 | 基线 | 范围、D1–D18 决策、验收、风险 |
| `01_总项目计划_PMO` | v2.2 | 2026-10 | 基线 | 三阶段里程碑、12 周甘特 |
| `02_工作文档_项目经理PM` | v2.2 / v3 | 2026-10 | 基线 | 周任务与人日 |
| `03_工作文档_后端开发BE` | v2.2 | 2026-10 | 基线 | 后端红线与实现要点 |
| `04_工作文档_Web前端FE` | v2.2 | 2026-10 | 基线 | Web 重写与换肤 |
| `05_工作文档_小程序开发MP` | v2.2 | 2026-10 | 基线 | 小程序换肤与新增页 |
| `06_工作文档_文档与测试QA` | v2.2 | 2026-10 | 基线 | 验收与测试 |
| `07_五角色交叉评审报告` | v1.0 | 2026-10 | 历史 | 评审记录，不改 |
| `07_评审提示词_供其他Agent评审` | v1.0 | 2026-10 | 历史 | 提示词模板 |
| `08_评审采纳对照记录` | v1.0 | 2026-10 | 历史 | 采纳台账 |
| `09_v2.1修订记录` | v2.1 | 2026-10 | 参考 | 附三「数字唯一来源表」 |
| `10_W1执行清单` | v1.0 | 2026-10 | 历史 | W1 任务清单 |
| `10_v3变更日志_云托管与AI三级降级` | v3 | 2026-10 | 参考 | 云托管与 AI 降级 |
| `11_v2.1五角色复评` | v2.1 | 2026-10 | 参考 | 复评意见 |
| `12_W1需求确认书` | v2.2 | 2026-10 | 有效 | 做/不做/验收 |
| `13_API接口规范` | v1.0 | 2026-10 | 有效 | 103 存量 + 27 v2 契约 |
| `14_需求分析` | **v1.1** | 2026-11-03 | 待评审 | 46 条 FR、C 类冲突、TBD 主台账 |
| `15_数据库设计` | **v1.1** | 2026-11-03 | 待评审 | 25 表/304 列、G 判定、TBD 引用 |
| `16_ER图` | **v1.1** | 2026-11-03 | 待评审 | 35 条关系、5 张状态机、孤儿检查 |
| `17_需求数据模型追溯矩阵` | **v1.1** | 2026-11-03 | 待评审 | M1/M2/M3、缺口主台账 |
| `18_需求分析报告_结构化与面向对象建模` | **v1.0** | 2026-11-03 | 新增 | 三大文档 + 六视图建模 |
| `_work/00_事实基线与目录` | **v1.1** | 2026-11-03 | 历史快照 | GATE 基线，已执行 |

## 二、2026-11-03 修订（按评审报告整改）

评审输入：六份文档（00/14/15/16/17/99）的审查报告，P0 三项、P1 三项、P2 五项。

### P0

| # | 问题 | 处置 | 落到哪 |
|---|---|---|---|
| P0-1 | G-09「直接补进 V1」与「Flyway 已执行 V1–V7」自相矛盾 | 定性为**该窗口期已关闭**：4 条菜单在首次迁移前补入并随 V1 执行，V1 冻结；后续只能新增 V8。99 §9 重写，删除原「直接补进 V1」方案，改为「G-09 已关闭 + V8 草稿示例」 | `15` §6、`17` §7、`99` §9、`00` §3 |
| P0-2 | M2（需求→接口）矩阵不存在 | 新增 `17` §4 M2：103 存量端点按 14 个 Controller 归集、27 个 v2 端点逐个映射 FR、4 条无接口 FR 单列、反查结论 | `17` §4、`14` §9、`00` §4.4 |
| P0-3 | V7「账单 9601」无证据 | 改为引用 V7 脚本行 + 三段可复现 SQL（迁移前查询、迁移后查询、反向校验），并说明 V7 是集合式回填而非硬编码单号 | `15` §2.3 |

### P1

| # | 问题 | 处置 |
|---|---|---|
| P1-4 | `00` 滞后于 15/17 | 升 v1.1：V1–V6→V1–V7、G-01..G-12→G-01..G-15、REL-45→REL-35、四张→五张状态机、10 段→13 段；状态改为「历史快照 + 索引」，事实以 14/15/17 为准 |
| P1-5 | TBD 编号在两份文档冲突 | `14` §8.2 定为唯一主台账（T-01..T-11），`15` §10 改为引用表，不再自编号 |
| P1-6 | G 台账双编号漂移 | `17` §7 升级为唯一权威台账（编号/证据/影响/判定/落点/状态），`15` §6 保留数据库侧判定并加状态列，`00` §3 改为指针 |

### P2

| # | 建议 | 处置 |
|---|---|---|
| P2-1 | 租约唯一性无 DB 约束 | 新增 G-15；99 §13 给生成列 + 唯一键方案；16 §5 补对账 SQL；14 FR-LEASE-01 显式标注「DB 不保证」 |
| P2-2 | `sys_user.role` 与 `user_role` 双源 | 新增 G-14；99 §12 给「role 降级为只读快照」方案；14 新增 T-10 |
| P2-3 | 99 §2 漏 `hrent_rent.renting_id` 的 FK | 99 §2 补预检 + DRAFT DDL，并注明预检 SQL 以 16 §5 为权威版本 |
| P2-4 | `ai_feedback.position` 缺 CHECK | 99 §1 增加「附带 2」；15 §3.8 补取值说明；G-01 判定中并入 |
| P2-5 | 孤儿检查 SQL 两处重复 | 16 §5 标注为权威版本，99 §2 改为引用，不再各写一份 |

### 附带修正

| 项 | 说明 |
|---|---|
| 覆盖度口径 | `FR-HOUSE-06` 与 `FR-RATE-06` 是同一能力（同端点 `/api/v2/rating/audit`），M1 按「有契约」计入前者，统计由 12/4/18/7/5 调整为 **12/4/19/6/5**，含契约覆盖率 74%→76% |
| 变更同步点 | 由「7 处」扩为「8 处」，增加 `docs/18` |
| 新增文档 | `18_需求分析报告_结构化与面向对象建模`，按课程「三大文档 + 六视图」组织，数字与 14/15/17 对齐 |

## 三、2026-11-03 23:08 复核（数据库恢复后补测）

上一轮整改时 `MySQL80` 服务处于 Stopped 且无权限启动，涉及数据库的结论只能引用脚本行号与迁移时的记录。服务恢复后补测如下，结论全部成立，其中一处数值被纠正：

| 复核项 | 结果 |
|---|---|
| 表 / 列 | 25 张业务表 + `flyway_schema_history`；业务列 304（含历史表 314） |
| Flyway 历史 | 8 条（baseline 0 + V1–V7）全部 `success=1` |
| `verify.sql` | 20 项全过，无一项「实际 ≠ 期望」 |
| V7 命中行 | `hrent_rent` 1 行：`id=9601`、`renting_id=9501`、`period=2026-12`、`amount_fen=50000`、`bill_status=UNPAID` |
| V7 生成明细 | `bill_item` 1 行：`rent_id=9601`、`RENT`、`50000`；幂等复跑未重复插入 |
| 无明细账单 | 0（3.1/3.2 归零） |
| 约束 | CHECK 12 条、FOREIGN KEY 0 条 |
| 新表审计列 | `create_by/update_by` 在 12 张新表命中 0；`del_flag` 仅 `review`、`repair_order` |
| G-06 预检 | `period IS NULL` 计数 = 0，可直接改 NOT NULL |
| G-14 预检 | 3 个账号 `role`(2/1/0) 与 `user_role`(ADMIN/LANDLORD/TENANT) 一一对应，无冲突 |
| G-15 预检 | 1 条生效租约，`HAVING COUNT(*)>1` 为空 |
| 行数 | 存量 13 表合计 55 行；新增 12 表除 `bill_item` 外全为 0 |
| 持久层 | `PersistenceSmokeTest` 通过：`PERSISTENCE_SMOKE_OK`，12 个 Dao 无 `BindingException` |
| 启动校验 | 应用启动：`Successfully validated 8 migrations` + `Schema house_rent is up to date. No migration necessary.`，证明 V1–V7 checksum 未被事后改动 |
| 接口回归 | `POST /hrent/sysCommon/getBaseInfo` → 200；`POST /hrent/houseResource/findPage` → 200，返回 2 套房源，与库内一致 |

**纠正**：`docs/15` §2.3 原写「迁移前 `period=NULL`」，实测为 `period='2026-12'`（V5 已从 `payable_time` 回填）。已改为实测值，并补上 `amount_fen=50000` 等字段。

## 四、待办

| 事项 | 归属 | 触发条件 |
|---|---|---|
| 评审通过后把 14/15/16/17 升 v1.2（去掉「待评审」） | PM | 评审会 |
| G-01/G-06/G-14/G-15 若采纳，合并为一个 V8 落地 | BE | 评审会 |
| `verify.sql` 增补孤儿检查与租约唯一性两项 | BE | V8 落地后 |
| 问卷与管理员访谈回收后补 `docs/18` §1.2.2/§1.2.3 | PM | W2 |

## 五、2026-11-04 00:35 新增 Word 交付版

| 项 | 内容 |
|---|---|
| 产物 | `docs/房屋租赁系统需求分析报告.docx`（2.3 MB） |
| 来源 | `docs/18_需求分析报告_结构化与面向对象建模.md`，内容未改写，仅排版 |
| 结构 | 封面 + 目录（13 条，Word TOC 域）+ 正文 8 章 + 5 个附录 |
| 图表 | 16 张图（全部由 Mermaid 渲染为 PNG 嵌入）、40 张表格、页脚页码 |
| 版式 | 正文宋体小四、首行缩进 2 字符；标题黑体；A4，自动分章另起页 |

排版过程中对源文档做了一处结构调整：原「0 层数据流图」单图高宽比 1.7，嵌入 A4 后高度 25.3 cm 会被裁切，故拆为「图 3 前台业务」与「图 4 交易与运营」两张，并在正文补一句衔接说明；图号顺延，全部 16 条图注重新编号。

工作脚本与中间产物：`docs/_work/md2html.py`（Markdown→HTML）、`docs/_work/diagrams/`（16 张 PNG）、`output/req-20261103-2328/`（HTML 与转换中间态）。

> 打开时 Word 会提示「是否更新域」，选「是」后目录页码自动填好。

---

## 六、2026-11-11 起 · v2 前后端功能开发迭代

> 依据：`docs/13_API接口规范.md` §4（新增接口契约）、`docs/01` 本期交付清单、`docs/12` 需求确认书。
> 边界：V1–V7 迁移与持久层已验证（本文件 §三）；本次只补齐 `/api/v2` 的 Controller/Service 与 Web 管理端页面，**存量 103 个接口零改动**。
> 日志读法：本文件后续每次读取一律**从末尾倒数 60 行起**，快速定位最近进展；每批改动完成后立即追加一小节。

### 6.1 改动前基线核对（实测）

| 项 | 实测结论 |
|---|---|
| 持久层 | 12 个新模块 Dao + XML 齐备（review/repair_order/bill_item/ai_feedback/lease_log 等），含 `updateStatusWithVersion`、`findByIdemKey` 等 |
| 业务层 | `/api/v2` 下 **0 个** Controller / Service；新功能接口全部未实现 |
| 前端 | Vue2 + ElementUI + webpack3，仅 11 个存量页面，无 v2 页面与 v2 请求封装 |
| 编译环境 | JDK 17.0.14、Node 22.22.2；Maven 在 `<LOCAL_PATH>/.m2/apache-maven-3.9.16/bin/mvn.cmd`（不在 PATH）；本地仓库 570 个 jar |
| 启动配置 | `server.port=8888`、`context-path=/hrent`（与 13 号文档 §2.1 写的 8080 无 context-path **不一致**） |

### 6.2 待定登记（详见 `docs/19_待定事项清单.md`）

| # | 待定问题 | 临时处置 |
|---|---|---|
| T-01 | Base URL 文档写 8080 无 context-path，实测 8888 + /hrent | v2 前端沿用存量 baseURL，文档口径待 PM 确认 |
| T-02 | 租约状态：契约用字符串 ACTIVE/TERMINATED，DB 用 TINYINT 0/1/2/3 | 接口层保持字符串，Service 做映射，DDL 是否改待定 |
| T-03 | 报修取消规则：§4.4 状态机写「任意非终态可取消」，接口表写「仅 CREATED 可取消」 | 按更严格的「仅 CREATED」实现，待 PM 裁决 |
| T-04 | AI 三级降级需 Ollama 花生壳地址 / DeepSeek API Key | 本期只落地 rule 模式，外部凭据走环境变量，不硬编码 |
| T-05 | 上传压缩到 200KB + 400px 缩略图需新增图像库 | 本期实现白名单/大小/UUID 重命名，压缩与缩略图待定 |
| T-06 | 贝叶斯冷启动参数 C、m 未在契约中给具体值 | 暂取 C=3、m=4.0，待需求方确认 |
| T-07 | `jjwt 0.9.1` 在 JDK17 缺 `javax.xml.bind`（W1 依赖 spike 未闭环） | 不改 pom；启动时需 `--add-opens` 或 JDK8，待 W1 决策 |
| T-08 | 存量 `src/config.js` 硬编码地图 Key 与 baseUrl | 本次不动存量，列入二期改造 |

### 6.3 改动记录

| 批次 | 时间 | 改动 | 校验 |
|---|---|---|---|
| B0 | 2026-11-11 | 基线核对 + 待定清单落档 + 新建 `api/v2`、`api/dao`、`mapper/api`、`view/v2` 目录 | 目录已创建 |
| B1 | 2026-11-11 | 新增 v2 公共层 10 类 + `V2Mapper` 接口 + `mapper/api/V2Mapper.xml`（18 条参数化 SQL） | javac 全量编译通过，`api` 包 11 个 class；存量源码 0 改动 |

**B1 关键信息**：

- 统一响应体 `{code:int, message, data, traceId}`，HTTP 恒 200；`@RestControllerAdvice(basePackages="com.wt.hrentSystem.api.v2")` 限定异常处理作用域，**不会改变存量 14 个 Controller 的异常行为**。
- `V2Context` 严格鉴权：无 token / token 失效 → 600。存量 `UserUtils` 的「回退管理员」兜底在 v2 被禁用（防真实越权）；越权统一 403。
- `Req` 统一做必填/类型/长度/枚举/范围校验，非法入参一律 400；金额只接受整数「分」；`pageSize` 上限 50。
- `V2TraceFilter` 只对 `/api/v2/**` 打点（traceId + 耗时），异常交给上层处理，不静默吞错。
- `V2Mapper` 放在 `com.wt.hrentSystem.api.dao`，**复用启动类已有的 `@MapperScan("com.wt.hrentSystem.*.dao")`**，无需改存量配置；XML 全部 `#{}` 参数化，`LIKE` 用 `CONCAT('%',#{x},'%')`，排序字段白名单固化。
- 校验方式说明：`mvn` 因沙箱无外网无法解析 `spring-boot-starter-parent:2.5.5`（aliyun 不可达），改用 `javac` + 本地仓库 570 个 jar 对**全部后端源码**做编译校验，等价覆盖语法与类型引用；运行时启动验证需在有外网的机器执行 `mvn spring-boot:run`。

| B2 | 2026-11-11 | 新增评分 6 接口 + 租约 3 接口（`RatingService/Controller`、`LeaseService/Controller`）+ `Rows` 行转换工具；新建 `docs/19_待定事项清单.md` 登记 11 项 | javac 全量编译通过，`api` 包 16 个 class；存量源码 0 改动 |

**B2 关键信息**：

- 评分资格链：租约 `status=TERMINATED`（DB=2）→ `actualEndDate` 非空 → `今天 ≤ 退租日+30 天` → 评价对象与租约的 houseId/landlordId 一致 → 评价人必须是租客本人（**管理员不豁免**）。任一不满足分别返回 404/422/403。
- 重复提交双兜底：应用层查 `review` + DB 唯一键 `uk_reviewer_target`，捕获 `DuplicateKeyException` → 409，不落第二条脏数据。
- 展示分 = `0.4×平台核验分 + 0.6×贝叶斯平滑用户分`，先验 `C=3 / m=4.0`，冷启动 0 评价时 `isNew=true` 且分 = 4.0（满足「≥3.0」硬门槛）；申诉中/隐藏评价不计入均分。
- 平台核验 8 项权重合计 1.00，仅管理员可执行；房东回复每条一次、≤100 字。
- 租约：`TINYINT 0/1/2/3 ↔ ACTIVE/EXPIRED/TERMINATED/CANCELLED` 映射集中在 `LeaseService`，接口层保持字符串；`terminate` 用 `version` 乐观锁，影响行数 0 → 409，并写 `lease_log` 与返回 `reviewWindowUntil`。
- 脱敏：评价列表用户名统一显示「张**」；异常日志只打 traceId + 业务码，不打印 token/密码/手机号。
- 新增待定 T-09（OVERDUE 口径）/ T-10（编译环境）/ T-11（AI 匿名白名单），均已写入 `docs/19`。

| B3 | 2026-11-11 | 新增报修 8 接口（状态机 + 乐观锁 + 幂等 + 流转日志）与账单 6 接口（生成 / 标记已缴 / 确认到账 / 明细对账） | javac 全量编译通过，`api` 包 20 个 class；全部 Mapper XML 解析通过 |

**B3 关键信息**：

- 报修 5 个动作统一由 `POST /api/v2/repair/{id}/{action}` 分发，`action` 白名单 `accept/arrive/complete/confirm/cancel`；非法跳转 409 且 message 写明「当前状态 / 需要状态」；`version` 乐观锁影响行数 0 → 409（抢单失败）。
- 取消按待定 T-03 采用**仅 CREATED 可取消**；若评审裁决改为「任意非终态」，只需改 `RepairService.edgeOf` 一处。
- 幂等双兜底：`Idempotency-Key` 命中 `findByIdemKey` 直接返回首次结果；并发插入撞 `uk_idem` 捕获 `DuplicateKeyException` 后回查返回，不产生第二条工单。
- 每次流转写 `repair_log`（from/to/操作人角色/备注），图片分 `SUBMIT` / `COMPLETE` 两阶段存储，详情页组装 `logs[]` 时间线。
- 账单金额全链路 `long fen`；生成时 `(renting_id, period)` 唯一 → 409，并同步写一条 `RENT` 明细；详情校验 `SUM(items.amountFen) == amountFen`，不等则 **500 + error 日志**，不静默放过对账错误。
- 逾期按 `payable_time < NOW()` 动态判定（兼容 V5 已落库的 `OVERDUE`，待定 T-09）；标记已缴 / 确认到账均用 `version` 乐观锁；重复确认返回 200 且 `idempotentHit=true`，不累加金额。
- 越权：账单访客限租客本人 / 房源房东 / 管理员；标记已缴限租客，确认到账限房东，其余 403。

| B4 | 2026-11-11 | 新增 AI 3 接口（规则推荐 / 模板理由 / 反馈埋点）与文件上传 1 接口；`application.yml` 增 `multipart` 5MB 上限 | javac 全量编译通过，`api` 包 24 个 class、6 个 Controller；存量接口 0 改动 |

**B4 关键信息**：

- AI 本期只落地 `mode=rule`：filters 优先、中文 query 规则补空（预算 / 几房 / `XX区` / 养宠 / 地铁 / 电梯 / 采光）→ 参数化检索 → 加权打分 → 模板理由；**模型不可用时仍返回 200**，无匹配时后端自动放宽预算 20% 并改 message。
- 三级降级（local Ollama → DeepSeek → rule）未接入，凭据走环境变量、不硬编码（待定 T-04）；`recommend`/`reason` 允许匿名演示，`feedback` 强制登录（待定 T-11）。
- 埋点 query 入库前做手机号掩码 `138****0001`，不存明文。
- 上传安全：扩展名 + Content-Type 双白名单、≤5MB、UUID 重命名（丢弃原始文件名）、路径 `normalize()+startsWith(dir)` 防穿越、落盘失败记 error 日志后返回 500，**不静默吞 IOException**。
- 压缩 / 400px 缩略图未实现（待定 T-05）：`thumbUrl` 与 `url` 同值，并显式返回 `compressed=false`、`width/height=null`，**不伪造宽高数据**。
- `application.yml` 仅新增 `spring.servlet.multipart` 两个键（存量 datasource/flyway/server 配置未动），否则 Boot 默认 1MB 会在进 Controller 前拦截 5MB 上传。
- 新增待定 T-12（契约 `/uploads/...` 与存量静态映射 `/images/**` 不一致）已写入 `docs/19`。

| B5 | 2026-11-11 | 前端新增 `src/network/v2.js` + 4 个管理端页面（`RatingManage` / `RepairManage` / `BillManage` / `AiRecommend`），接入路由与「新功能 v2」菜单 | `npm run build` 成功（webpack 3.12，app.js 150 kB）；存量页面 0 改动 |

**B5 关键信息**：

- v2 网络层与存量 `core.js` 完全隔离：注入 `token` 头、统一解包 `{code,message,data,traceId}`、600 清 token 跳登录、300ms 内同 method+url+参数请求合并；AI 20s / 上传 60s 独立超时；后端特殊 message 透传为 `data._notice` 供页面提示。
- 金额换算只在 `fen2yuan` / `yuan2fen` 两处，页面只负责展示 `¥`，不出现散落的 `*100`。
- 四个页面均做错误提示（含 traceId）、loading、空列表；`catch` 里 `console.error` 留证据，不静默吞错；`el-prompt` 的用户取消单独识别，不当成接口错误。
- 路由新增 4 条 children，菜单新增 1 个 `children` 子菜单「新功能 v2」；存量 11 个页面与菜单项未改动（存量菜单用 `childList` 字段名导致子菜单不渲染，属历史问题，本次不动）。
- 构建命令：`NODE_OPTIONS=--openssl-legacy-provider npm run build`（Node 22 + webpack3 的 MD4 需要 legacy provider）；`dist/` 构建产物被重新生成，属可再生文件。
- baseUrl 仍取自存量 `src/config.js`（待定 T-01/T-08），未新增第二处硬编码地址。

| B6 | 2026-11-11 | 维护根 / 后端 / 前端三份 README；输出 `19` 待定清单、`20` 功能描述、`21` 测试、`22` 下一步需求四份交付文档；最终全量校验 | javac 0 error；25 份 Mapper XML 解析全过；前端 `app.js` 149,603 B；新增前端文件无硬编码地址/密钥 |

**B6 关键信息（收尾）**：

- 三份 README 各追加一章：根 README 写接口分组 / 启动 / 校验 / 文档索引；`hrent-api/README.md` 写 v2 目录结构、设计约束、编译与启动命令；`hrent-web/README.md` 写页面清单、Node 17+ 构建参数、联调前提。
- 交付文档：`19_待定事项清单.md`（13 项，含临时处置与闭环规则）、`20_功能描述文档.md`（6 组接口 + 4 页面 + 全局约定）、`21_测试文档.md`（6 类已执行静态校验 + 约 60 条待联调用例 + 缺陷风险）、`22_下一步需求文档.md`（待定闭环 + P0/P1/P2 backlog + 排期 + 5 条验收门槛）。
- **测试纪律**：本环境无法启动 Spring Boot，所有 HTTP 用例一律标 `待联调`，结果列不预填「通过」；整体判定明确写 **不可放行**，上线前须在联网机器补 `mvn clean package`、启动冒烟、103 个老接口快照 diff。
- 下一步第一优先级是**裁决 5 项 P0 待定 + 补运行时证据**，不是继续加功能；功能缺口（AI 三级降级、图片压缩、小程序端）已按 P1/P2 分级排入 backlog。
- 本文件后续读取仍从末尾倒数 60 行起；下一批改动从 **B7** 开始编号，继续在 §6.3 追加，不要改写已固化批次。

---

### 6.4 本迭代交付清单（截至 B6）

| 类别 | 数量 | 说明 |
|---|---|---|
| 后端新增 | 24 个 java + 1 份 XML | `api/v2` 18 个类 + `api/dao/V2Mapper.java`；6 个 Controller / 23 个路由映射 |
| 前端新增 | 5 个文件 + 2 处挂载 | `network/v2.js` + 4 个页面；router +4 条、MyMenu +1 组 |
| 配置改动 | 3 处 | `application.yml` +2 键、router、MyMenu |
| 文档 | 8 份 | CHANGELOG、19–22、根 / api / web 三份 README |
| 待定 | 13 项 | T-01 ~ T-13，见 `docs/19` |

---

| B7 | 2026-11-11 | 公开发布：白名单复制 1071 个文件到**独立发布副本**，脱敏 63+ 处、二次补齐 5 个文件；推送至 GitHub `gongmao201/houserentSysPublic`（commit `d8ebdf4`） | 远端重新克隆验证：871 个跟踪文件、敏感扫描 **HITS=0**、排除项残留 0；`git ls-remote` 确认 main = `d8ebdf4` |

**B7 关键信息**：

- **源项目零改动**：所有替换只在 `/tmp/houserent-release` 发布副本中进行，源工作副本的业务代码、配置、文档一律未动（`git status` 的 16 项均为 B1–B6 的既有改动）。
- **历史处理**：本地仓库唯一的 `Initial commit` 含未脱敏文件，未推送；目标仓库在其原有 `LICENSE` 提交上追加一次**全新提交**，公共历史不含本地敏感快照。提交作者使用中性身份 `houserent-public <noreply@example.invalid>`，未沿用全局 git 的个人邮箱。
- **清理类型**：微信 AppID/AppSecret 明文、腾讯地图 Key、JWT 硬编码密钥、数据库口令/用户名/连接串、演示口令明文与哈希、内网 IP、本机绝对路径、作者手机号/微信/QQ/QQ 群、第三方推广、示例数据姓名/手机号/邮箱、页面截图章节。
- **排除类型**：`.workbuddy/`、3 个 `.rar`、`.docx` 交付版、`picture/`+`output/`+`docs/_work/` 截图与中间产物、`node_modules`/`target`/`hrent-web/dist`、重复的 `README2.md`（含明文密钥）。程序 UI 图标与小程序开源 UI 组件目录保留。
- **占位符**：`<YOUR_WECHAT_APPID>` / `<YOUR_WECHAT_APPSECRET>` / `<YOUR_QQMAP_KEY>` / `<SET_VIA_ENV_JWT_SECRET>` / `<DEMO_PASSWORD>`；`application.yml` 改为 `${DB_URL:...}`、`${DB_USERNAME:...}`、`${DB_PASSWORD:}`、`${JWT_SECRET:}` 环境变量优先。配置方法见仓库根目录 `PUBLISH_NOTES.md`。
- **提示**：目标仓库原有 `Initial commit` 的作者邮箱为仓库所有者自身的 Git 身份，未改写（改写需 force push，风险更高，未授权）；如需连历史一并脱敏，须显式授权后做历史重写。
- 原始源码来自第三方分发包，公开再分发前请确认授权与署名要求（已写入 `PUBLISH_NOTES.md` 第五节）。

---

| B8 | 2026-11-11 | 裁决 5 项 P0 待定（T-01/02/03/07/10）+ 落地 Maven Wrapper 与配套配置，输出 `docs/23_P0决策与验证闭环.md` | Wrapper 生成实测 `BUILD SUCCESS`；两份 `.gitignore` 已放行 `mvnw*`/`.mvn/wrapper/`；`jaxb-api:2.3.1` 已入 `pom.xml` |

**B8 关键信息**：

- **T-10（mvn 位置不一致）裁决为方案 A · Maven Wrapper**：`mvn -N wrapper:wrapper -Dmaven=3.9.16 -Dtype=only-script` 生成 `hrent-api/mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`（仅 3 个文件、约 20 KB）。**否决**把 570 个 jar 的 `.m2/repository` 提交进仓库（体积/许可证/凭据风险），也否决「只写探测脚本」（仍依赖机器已装 Maven 且版本不定）。
- **根因**：用户级 `~/.m2/settings.xml` 把 `localRepository` 写死为 `<LOCAL_PATH>/.m2/repository`，同事机器路径必然不同；叠加两份 `.gitignore` 原本忽略 `.mvn`/`mvnw`，导致 Wrapper 根本进不了仓库。
- **配套**：新增 `hrent-api/.mvn/settings.xml` 作为**可选模板**（无绝对路径、无凭据、无镜像硬编码），不通过 `maven.config` 强制引用，避免覆盖同事本机镜像配置；`.mvn/jvm.config` 不生成（JDK8 兼容）。
- 另 4 项裁决：T-01 以代码实测 `8888 + /hrent` 为准、只改文档；T-02 保留 `LeaseService` 单点枚举映射不改 DDL；T-03 维持「仅 CREATED 可取消」；T-07 不升级 jjwt，`pom.xml` 加 `jaxb-api:2.3.1` 兜底。
- 待确认假设 5 条（H1–H5）写入 `docs/23` §4，含「老接口快照基线需 QA 产出」——该假设不成立直接导致 B-1 阻塞。

| B9 | 2026-11-11 | 按唯一优先级跑验证闭环：V1 构建 → V2 启动 → V3 用例 → V4 老接口回归；修复 4 个实测缺陷并回填 4 份文档 | V1 `BUILD SUCCESS`（3 tests / 0 fail，jar 29 MB）；V2 `Started in 21.155s`；V3 **32 PASS / 0 FAIL**；V4 103 接口 0 回归（2 个存量死接口除外） |

**B9 关键信息（缺陷与实测数据）**：

- **V3 三轮收敛**：27/5 → 29/3 → **32/0**。暴露并修复 3 个 v2 自身缺陷：
  1. `V2Mapper.xml` 的 `leaseCols` 引用 `g.tenant_name`，但 `hrent_house_renting` 无该列（MySQL 1054）→ 删除该列；
  2. `FileController.upload` 的 `@RequestParam("file")` 绑定先于鉴权，未登录返回 500 而非 600 → 改 `required=false` + 鉴权前置；
  3. `V2Time.iso/day` 对 `java.sql.Date` 调 `toInstant()`，该方法在 JDBC 日期类型上**固定抛 `UnsupportedOperationException`** → 改 `Instant.ofEpochMilli(date.getTime())`。
- **修复 1 个存量兼容缺陷（D4）**：MySQL 8 将 `system` 列为保留字，`MenuDao.xml` / `MessageDao.xml` 中该列未加反引号 → `/menu/getLoginMenu` 恒 500。**仅加反引号，不改逻辑**；两个文件在本次之前从未改动（`git status` 为空可证）。
- **V4 口径**：103 个存量接口中，写语义 38 个 **SKIP**（`delete` 无 id 时 `where 1=1` 会清表，禁止在种子库实调）；实调 65 个读接口 → 49 个空参即通、14 个补 `{"pageNo":1,"pageSize":5}` 后通、**2 个恒 500 的死接口**（`/dict/get` 的 DAO SQL 用 `limit #{offset},#{limit}` 与 `BaseService.get(String id)` 参数冲突；`/houseResource/findAll` 未设 `currentUser` 触发 OGNL `source is null`）。两者 `hrent-web` 与小程序**均无调用**，不修，登记入 `docs/22` §8.2。
- **阻塞 B-1（重要）**：老接口「改造前快照 diff」**无法执行** —— 基线 `pom.xml` 无 `jaxb-api`，JDK17 下 `jjwt 0.9.1` 签发 token 抛 `NoClassDefFoundError`，**基线登录不了**，无法构造同口径对照。故 V4 结论只能表述为「逐接口可达性 0 回归」，**不等于**快照 diff 0 差异；硬门槛 1 仍未按原定义达成。
- **阻塞 B-2**：沙箱注入 `SERVER_PORT=52051` 且端口被占用 → 首次启动 `PortInUseException`，加 `--server.port=8888` 后成功；同事机器无此变量，不影响产物。
- 文档回填：`docs/19` 5 项 P0 状态改「已裁决/已闭环」；`docs/21` 新增第七章（V1–V4 实测与缺陷登记）；`docs/22` 新增第八章（8.1 已闭环 / 8.2 新增待办 / 8.3 环境交接两条）；`docs/23` 第六、七章回填实测。
- 仍未放行：38 个写接口未回归、v2 四页面未做浏览器级联调、2 个存量死接口未修。下一批从 **B10** 编号。

| B10 | 2026-11-11 | 采纳方案 A 后补跨平台一键启动脚本（Windows/macOS/Linux），修正 `.gitignore`/`.gitattributes`，新增部署模板与 `docs/24`，同步 README | 两平台脚本各 9 项实测全通过；后端构建 1m46s、前端 2m45s；修复 7 个脚本缺陷 |

**B10 关键信息（新增文件与实测缺陷）**：

- **采纳 A 的落地**：`hrent-api/mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`、`.mvn/settings.xml` 全部可被 git 跟踪（`git check-ignore` 返回 `ignored=1`，`git status` 显示 `??`）。根与 `hrent-api` 两份 `.gitignore` 的放行规则已验证生效。
- **新增一键启动（6 个文件）**：
  - `start.sh`：Linux/macOS/WSL/Git Bash 主入口，支持 `start/run/stop/restart/status/logs/build/build-web/help`；
  - `start.cmd` + `scripts/hrent.ps1`：Windows 入口与实现（PS 5.1 兼容，文件为 UTF-8 BOM）；
  - `.env.example`：9 个环境变量占位模板，不含真实凭据；
  - `deploy/hrent.service`、`deploy/nginx.conf.example`：服务器部署模板。
- **端口变量命名决策**：脚本只认 `HRENT_PORT`、**不读 `SERVER_PORT`**。依据是 B9 记录的沙箱注入事故（8888 被改成 52051 导致 `PortInUseException`）；启动时再显式追加 `--server.port` 做双保险（命令行优先级高于环境变量）。
- **实测暴露并修复的 7 个脚本缺陷**（均为真机跑出来的，非静态推断）：
  1. Git Bash 下 `$!` 是 MSYS 包装进程号（767），真实 Java 进程是另一个 Windows PID（25324）→ `status` 误报未运行；改用 `jps -l` 匹配 jar 名（jps 输出 Windows PID）；
  2. 端口反查兜底未校验进程名，被一个监听 8890 的 Python 进程骗成「服务已在运行」，阻塞启动 → 加 `tasklist`/`ps -o comm=` 校验；
  3. Windows `taskkill` 不带 `/F` 对无窗口控制台 Java 无效，脚本空等 15 秒 → Windows 分支直接 `/F`，等待缩到 6 秒；
  4. PowerShell 在 `$ErrorActionPreference='Stop'` 下用 `2>&1` 捕获 `java -version`，把 JVM 的 stderr 当异常抛 → 改 `System.Diagnostics.Process` 重定向；
  5. `Start-Process` 在同时存在 `HTTPS_PROXY`/`https_proxy` 时抛「字典中的关键字冲突」直接崩 → 启动前归一化重复环境变量键；
  6. `Get-NetTCPConnection` 无匹配时抛异常，退回 netstat 又匹配到 `TIME_WAIT` → cmdlet 优先 + 兜底过滤 `LISTENING`；
  7. `status` 把端口检测放在健康检查前，启动瞬间误报「端口未监听」→ 改为健康检查为主、端口为辅。
- **日志策略**：每次 `start` 前把 `logs/hrent.log` 轮转为 `.log.1`（只留一份历史），避免长期运行无限增长；Windows 侧因 `Start-Process` 只能覆盖写，额外产出 `hrent.err.log` 存 JVM 自身错误。
- **`.gitattributes` 修正**：原文件把 `*.js/*.css/*.html/*.vue` 标记为 `linguist-language=java`（GitHub 语言统计伪装），属误导性元数据，不适合公开仓库 → 改为换行符策略（`*.sh`/`mvnw` 强制 LF，`*.cmd`/`*.ps1` 为 CRLF）。**此项会改变 GitHub 语言统计结果**。
- **`.gitignore` 新增**：`logs/`、`*.pid`、`.env`、`.env.local`、`hrent-api/static/images/`、`hrent-web/dist/`。`.env` 已验证被忽略（第 42 行规则）。
- **文档**：新增 `docs/24_一键启动与部署指南.md`（10 章：快速开始/命令参考/环境变量/8 条平台差异实测/服务器部署/16 项 Bash 实测/9 项 PowerShell 实测/7 个缺陷/12 条排障/未覆盖项）；根 `README` 新增第八章并在 7.3 改为「一键启动优先」；`hrent-api/README` 7.3 同步指向脚本。
- **未覆盖（如实登记）**：Linux 真实发行版与 macOS 未实跑，仅按 POSIX 实现并在 Git Bash 验证；Docker 化、Windows 开机自启、logrotate 接入未做。均已写入 `docs/24` 第十章。
- 下一批从 **B11** 编号。

| B11 | 2026-11-11 | 公开发布准备：重建脱敏副本、二次扫描、本地提交 `d277360`；远端推送因网络 502 未完成，产出离线 bundle | 副本 1083 文件 / 6.3 MB；脱敏 44 处 / 18 文件；扫描 **HITS=0**；本地提交成功，远端仍为 `d8ebdf4` |
| B12 | 2026-11-11 | 修复 Windows 一键启动双击闪退：`start.cmd` 改纯 ASCII + CRLF、无参数运行保留窗口、重复启动改幂等、失败原因落盘 | 14 项实测全通过；`start.cmd` 非 ASCII 字节 0；从停止状态双击 19.2s 启动成功 |
| B13 | 2026-11-11 | 修复 `.env` 占位符导致数据库 1045、以及健康检查被代理劫持的假阳性；启动前增加占位符拦截 | 密码实测 `change-me` 失败 / `<LOCAL_DB_PASSWORD>` 成功；健康检查旧写法假阳性、新写法正确；`Started HrentApplication in 8.253s` |
| B14 | 2026-11-11 | 补齐「一键启动前端」能力：后端托管 `dist`（单进程）+ 新增 `dev` 热更新模式；前端去掉全部写死的主机地址 | `start` 后页面 200 / `app.js` 200（149,519 B）/ 接口 200；`dev` 12s 就绪、代理 `/hrent/**` 返回业务码 200；`stop` 后 8888/8080 均释放 |

**B11 关键信息（发布状态）**：

- **发布副本重建**（不使用增量，避免旧副本残留）：白名单复制 1083 个文件 / 6.3 MB，排除 `*.rar`、`*.docx`、`README2.md`、`picture/`、`output/`、`docs/_work/`、`node_modules`、`target`、`dist`、`.workbuddy`、`logs/`、`.env`。
- **脱敏 44 处 / 18 文件**：DB 用户名/口令/JWT 密钥外部化为 `${DB_USERNAME}`/`${DB_PASSWORD}`/`${JWT_SECRET}`；`TokenUtils` 硬编码兜底密钥→`change-me-token-secret`；真实微信 AppID `wx5c68…`→`touristappid`（官方游客模式）；内网 IP→`127.0.0.1`；本机绝对路径 7 处→`<LOCAL_PATH>`；隧道域名 11 处→`<TUNNEL_HOST>`；作者手机号/QQ/个人站点→`<CONTACT>`/`<PERSONAL_SITE>`；演示口令明文 7 处→`<DEMO_PASSWORD>`；SQL 注释 3 处改为「初始口令见 README」。
- **二次扫描**：393 个文本文件，敏感项 **HITS=0**、排除项 **PRESENT=0**。规则覆盖明文口令、硬编码密钥、内网 IP、本机路径、联系方式、隧道域名、`wx[0-9a-f]{16}` 形态 AppID、私有工作区目录。
- **修正 2 处扫描发现的真实残留**：`hrent-wechat/project.config.json` 的真实 AppID；`docs/CHANGELOG.md` 中对私有目录的直呼（改为中性表述）。
- **`PUBLISH_NOTES.md` 重写**：新增占位符替换清单（7 项）、排除清单、已知限制 5 条、安全提示。明确「演示账号为弱口令，对外部署前必须重置」。
- **本地提交 `d277360`**：243 文件变更（+6257 / −174），作者 `houserent-public <noreply@example.invalid>`（中性身份，未用本机 git 全局邮箱）。
- **推送失败（阻塞）**：代理返回 `CONNECT tunnel failed, response 502`，直连 `github.com:443` 21 秒超时；重试 3 次均失败。**远端 `main` 仍为 `d8ebdf4`，本轮改动尚未公开**。
- **离线交付**：生成 `<LOCAL_PATH>/houserent-public-d277360.bundle`（1.2 MB，`git bundle verify` 通过，含完整历史），配套 `推送说明.md` 给出三种推送方式与验证命令。
- **注意**：源项目目录仍为未脱敏开发版，**不可直接从该目录 push 到公开仓库**；发布必须走副本/bundle 路径。

**B12 关键信息（Windows 双击闪退修复）**：

- **现象**：资源管理器双击 `start.cmd`，窗口一闪即关，看不到任何结果；部分机器还会刷出若干「不是内部或外部命令」。
- **根因（三条叠加，均已复现）**：
  1. **编码**：`start.cmd` 为 UTF-8 无 BOM + LF，而 cmd.exe 按控制台代码页（中文 Windows = 936/GBK）解析批处理。中文 `REM` 注释被解码成乱码，乱码字节破坏注释边界，后半段被当命令执行 → `'埧灞嬬璧佺鐞嗙郴缁?路' 不是内部或外部命令` 等 5 条报错（原始输出留档于 `docs/24` §8.4）；
  2. **无停留**：脚本执行完立即 `exit`，窗口随进程退出关闭，成功/失败都来不及看；
  3. **误判失败**：服务已在运行时走 `Die`（返回码 1），重复双击被当成错误。
- **修复**：
  1. `start.cmd` 重写为**纯 ASCII + CRLF**（非 ASCII 字节 0），所有中文提示交回 `hrent.ps1`（UTF-8 BOM，不受控制台代码页影响）；
  2. 无参数运行（双击场景）结束时保留窗口并打印结果摘要；带参数（`status`/`stop`/`logs`…）不阻塞，保持可脚本化；`HRENT_NO_PAUSE=1` 可强制关闭停留；
  3. 重复启动改为**幂等成功**（返回码 0），并从进程命令行解析 `--server.port` 显示**真实端口**，避免 `--port 9999` 在服务占用 8888 时谎报地址；
  4. `hrent.ps1` 增加顶层 `try/catch`：未处理异常不再直接消失，而是打印可读原因 + 写入 `logs/hrent.error.log`（UTF-8 无 BOM）+ 返回码 2；
  5. `--port` 改用 `[int]::TryParse`，非法值给业务提示而非 .NET 异常栈；
  6. PID 文件校验加进程名判断（`java`），避免 PID 复用误判「已在运行」。
- **验证（14 项，均实测）**：文件属性、无参运行、窗口保留、从停止状态启动（19.2s）、重复启动、端口冲突（`--port 9999`）、外部占用（`--port 3306` → 指出 `PID 21632 mysqld`）、未知参数、非法端口、错误日志编码、`stop` 幂等、带参数不阻塞、损坏 PID 文件、状态查询不误报失败。明细见 `docs/24` §8.4。
- **对比修复前**：解析错误 5 条 → 0 条；双击后可见结果 0 行 → 完整摘要 + 日志路径；重复启动返回码 1 → 0。
- **已知不适用**：`%CMDCMDLINE%` 不能用于判断双击（真实双击时含嵌套引号，赋给 `set "VAR=..."` 会被截断），已改用「无参数即保留窗口」的规则；测试环境中由命令派生的子进程会在命令结束时被回收，与真实双击（explorer 启动）无关，已单列说明避免误判。

**B13 关键信息（数据库认证失败 + 健康检查假阳性）**：

- **现象**：B12 之后窗口不再闪退，错误完整可见，内容是 Flyway 连库抛 `Access denied for user 'root'@'localhost' (using password: YES)`。
- **根因 1（配置）**：`.env` 第 26 行是 `DB_PASSWORD=change-me`——从 `.env.example` 复制后从未替换。脚本把它注入 `SPRING_DATASOURCE_PASSWORD`，覆盖了 `application.yml` 的 `<LOCAL_DB_PASSWORD>`，而这个占位符并不是 MySQL 的真实密码。
  - 实测：用 `change-me` 连库 → `ERROR 1045`；用 `<LOCAL_DB_PASSWORD>` 连库 → `ok / 1`。
- **根因 2（脚本）**：健康检查 `Test-Health` 用 `curl -s` 只看退出码。环境里存在 `http_proxy` 时，连 `127.0.0.1` 也被发给代理，代理回 `502`，但 `curl` 仍返回 `exit=0` → 被判「健康」。后果是**启动失败被误报为启动成功**，上一轮排查中被这一点误导过。
- **修复**：
  1. `.env` 的 `DB_PASSWORD` 改为本机真实密码 `<LOCAL_DB_PASSWORD>`；
  2. `Test-Health` 加 `--noproxy "*"`，并用 `-w "%{http_code}"` 严格校验 200；
  3. 新增 `Assert-EnvReady`：启动前检测 `^change[-_]?me`，`DB_PASSWORD` 命中直接 `Die` 并指出行，`TOKEN_SECRET` 命中只告警（不阻塞启动）。
- **验证**：
  - 占位符拦截：1 秒内报「DB_PASSWORD 仍是模板占位符」，不再等 Spring 抛一屏栈；
  - 真实启动：日志 `Started HrentApplication in 8.253 seconds`，无 `Access denied`；
  - 健康检查对照（服务未运行时）：旧写法 `exit=0` 判「健康」（假阳性），新写法 `http=000 exit=7` 判「不健康」。
- **遗留提醒**：`.env` 的 `TOKEN_SECRET` 仍是 `change-me-to-a-random-32-bytes-secret`，能启动但 JWT 密钥可预测，对外部署前必须换成随机值。
- **顺带修复**：`status` 只判断 PID 是否存在，进程退出后 PID 被系统复用会误报「运行中」（实测 PID 30936 已被其他程序占用）。已加 `java` 进程名校验，与 `Start-App` 保持一致。

**B14 关键信息（一键启动后端 + Web 前端）**：

- **起点**：`start.cmd` 只拉起后端 JVM，前端仅有 `build-web`（构建到 `dist`，不提供访问），双击启动后只能开接口、看不到页面。
- **方案**：两条路并行，按用途选，互不冲突：
  1. **生产/演示（单进程，无需 Node 运行时）**：`WebMvcConfig` 新增 `/web/**` 到 `file:${HRENT_WEB_DIR}` 的映射；脚本在 `dist` 存在时注入该环境变量。访问 `http://<host>:8888/hrent/web/index.html`。hash 路由 + `assetsPublicPath: './'`，放子路径即可工作，无需 fallback 规则。
  2. **开发（热更新）**：新增 `dev` 命令，先起后端并等健康检查通过，再起 webpack-dev-server（默认 8080，`--web-port` 可改），访问 `http://localhost:8080/`。
- **前端去硬编码**（原违反「禁止硬编码 host/port」约定，见 `docs/19` T-01/T-08）：
  - `src/config.js`：`baseUrl` 由绝对地址改为 `/hrent`，`picLocal` 同步；
  - `src/components/common/Header.vue` 105/142 行：默认头像由绝对地址改为 `/hrent/default/head/head.png`（**这是真正的功能缺陷**，换端口或上域名后头像必 404）；
  - `config/index.js`：`proxyTable` 由 `{}` 改为把 `/hrent` 转发到 `HRENT_API_BASE`（默认 `http://127.0.0.1:8888`），让相对路径在 dev 模式可用。
- **脚本新增**：`Test-WebBuilt` / `Test-WebPidIsOurs` / `Test-WebUp` / `Start-WebDev` / `Stop-WebDev` / `Stop-WebNodeByCommandLine` / `Show-WebStatus`；`stop` 同时停前后端；`status` 同时显示；`logs --web` 看前端日志。
- **踩到的两个坑（均已修）**：
  1. **误杀构建进程**：`Stop-WebNodeByCommandLine` 最初用 `hrent-web|webpack-dev-server` 宽匹配，而 `npm run build` 的命令行同样含 `hrent-web` 路径，执行 `stop` 会把正在构建的前端进程杀掉（实测一次杀掉 3 个 node）。已收紧为仅匹配 `webpack-dev-server` / `webpack.dev.conf`，且**仅在 PID 文件存在但进程失效时**才兜底扫描。
  2. **dev 模式端口漂移**：webpack-dev-server 用 `portfinder`，端口被占用会静默改用下一个，脚本报告的地址就是假的。改为启动前先校验端口，再显式设 `PORT`，冲突时报出占用者而不是漂移。
- **验证（均实测）**：
  - 单进程：`start` 后 `index.html` 200（614 B）、`app.js` 200（149,519 B，与 dist 一致）、接口 200；`status` 同时输出后端与前端。
  - dev：12s 就绪；首页 200；`/hrent/sysCommon/getBaseInfo` 经代理返回 200（业务码 200）；`/app.js` 200（22.3 MB，含 eval source map，属 dev 正常体积）。
  - 停止：`stop` 后 8888 与 8080 均 FREE，前端 dev server 进程树被 `taskkill /F /T` 清干净（含 npm.cmd → cmd.exe → node.exe 三层）。
  - 硬编码：`hrent-web/src` 全目录 grep `localhost:8888|127.0.0.1:8888` **0 命中**；重新构建后 `dist/static/js/app.*.js` 亦 **HARDCODE=NO**。
- **两个入口已对齐**：`start.sh`（Unix/Git Bash）同步实现 `dev`、`build-web` 托管、`status` 前端识别、`stop` 连带停前端、`logs --web`，与 PowerShell 分支行为一致；两套脚本均实测通过。
- **未覆盖**：前端四个 v2 页面未做浏览器点选联调，仅验证到「页面与静态资源可 200 加载 + 接口经代理 200 + 返回体与直连一致」；Linux/macOS 实机仍未跑（仅 Git Bash）。
- **自定义端口停机缺陷（B14 补丁，已修）**：`HRENT_WEB_PORT=8099 ./start.sh dev` 之后直接 `./start.sh stop`，新进程不知道 8099，按默认 8080 反查必然落空，报「前端开发服务器未运行」却让 8099 一直监听（实测残留 `port8099=ALIVE`）。修复两处：
  1. 新增 `logs/hrent-web.port` 记录 dev 实际端口，`stop`/`status` 在未显式指定端口时回落到该值（优先级：`--web-port` > `HRENT_WEB_PORT` > 记录的端口 > 8080）；
  2. PID 文件回填为**真正的监听进程**（`port_owner_pid` / `Get-PortOwnerPid`）：`$!` 与 `npm.cmd` 都是包装进程，`kill -0` 能通过但按命令行判定为「不是 node」，导致下一步找不到进程。
  回归：Bash `HRENT_WEB_PORT=8099 dev` → 无参 `stop` → 8888/8099 双双 FREE；`dev --web-port 8099` → `status` 显示 `运行中（PID 4632）-> :8099` → `stop` → 双双 FREE。同时把 `HRENT_WEB_PORT` 补进 `.env.example` 与 `docs/24` 环境变量表。

