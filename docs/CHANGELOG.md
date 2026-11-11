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
| 编译环境 | JDK 17.0.14、Node 22.22.2；Maven 在 `<PROJECT_ROOT>/`（不在 PATH）；本地仓库 570 个 jar |
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

