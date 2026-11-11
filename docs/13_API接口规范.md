# 13 · API 接口规范（v1.0，W2 冻结）

> 适用：Web（Vue3）、小程序（原生 + Vant）、后端（Spring Boot 2.7.18）、QA（接口用例与回归基线）
> 依据：`hrent-api` 源码实测（14 个 Controller / **103 个接口**）+ `00` 主报告 v2.3
> 状态：**草案 → W2 末冻结**。冻结后任何字段变更走第 9 章流程，不允许口头约定。
> 一句话：**存量接口一个都不改，新接口全部走 `/api/v2`，两套并存到二期。**

---

## 1. 范围与三条底线

| 底线 | 说明 |
|---|---|
| **存量不动** | `/user/login`、`/houseResource/findPage` 等 103 个接口的路径、参数、响应结构**一律不改**，保证老小程序在线版本不 500 |
| **新接口带版本** | 新增模块一律 `/api/v2/**`，统一响应体、统一错误码、统一分页 |
| **契约先行** | W2 末 OpenAPI 全量冻结，FE/MP 按 mock 开发，联调只做"换 baseURL + 冒烟" |

---

## 2. 全局约定

### 2.1 环境与 Base URL

| 环境 | Base URL | 说明 |
|---|---|---|
| 本地 | `http://localhost:8080` | 无 context-path（已确认 `application.yml` 未配） |
| 测试 | `http://<测试机IP>:8080` | 局域网，供真机预览 |
| 演示/线上 | `https://<备案域名>` | 需备案 + HTTPS，未就绪前只用前两个 |

**前端禁止硬编码地址**：Web 用 `.env.development` / `.env.production` 的 `VITE_API_BASE`；小程序用 `config/env.js` 导出 `baseUrl`，按 `__wxConfig.envVersion` 切换。

### 2.2 路径与方法

| 类型 | 规范 | 示例 |
|---|---|---|
| 存量读 | `POST /模块/动作`（沿用，参数走 body 或 query） | `POST /houseResource/findPage` |
| 存量写 | `POST /模块/动作` | `POST /houseResource/save` |
| **v2 读** | `GET /api/v2/模块/资源/{id}` | `GET /api/v2/repair/{id}` |
| **v2 写** | `POST /api/v2/模块/资源` | `POST /api/v2/repair` |
| **v2 状态变更** | `POST /api/v2/模块/资源/{id}/动作` | `POST /api/v2/repair/{id}/accept` |

> 为什么 v2 状态变更不用 PUT/PATCH：动作语义（accept/confirm/terminate）比资源替换更清晰，且前端拦截器只需处理 GET/POST 两种。

### 2.3 鉴权

| 项 | 约定 |
|---|---|
| 请求头 | **`token: <jwt>`**（沿用存量，`UserUtils` 读的就是这个头） |
| 兼容 | v2 拦截器**同时接受** `Authorization: Bearer <jwt>`，但前端一律只用 `token` |
| 有效期 | 默认 7 天（`token.expire-days`），**不做 refresh token**（12 周内不引入） |
| 过期/未登录 | 业务码 **`600`**（沿用存量，**不要改成 401**，老前端只认 600） |
| 白名单 | `/user/login`、`/user/register`、`/wechat/login`、`/wechat/loginByOpenId`、`/sysCommon/**`、`/api/v2/ai/recommend`（体验用）、`/actuator/health` |

**换库风险**：W5 升 jjwt 0.11.5 后老 token 无法验签。必须 7 天双验期（新库签发 + 老库兜底验签），否则上线当天所有在线用户被踢。

### 2.4 统一响应体（v2）

```json
{
  "code": 200,
  "message": "ok",
  "data": {},
  "traceId": "8f3c2a1b4d5e"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | 业务码，见 2.6 |
| message | string | 面向用户的提示，**禁止返回堆栈或 SQL** |
| data | any | 成功时必有（可为 `null`）；失败时通常为 `null` |
| traceId | string | 贯穿日志；前端报错时提示用户复制此值 |

**HTTP 状态码策略（重要）**：v2 **统一返回 HTTP 200**，业务结果放在 `code`。
理由：存量 103 个接口全部是 HTTP 200，前端只写一套判断逻辑；若 v2 改用 4xx，axios 会抛异常，需要两套错误处理，12 周内不值得。
唯一例外：网关级错误（502/504）、文件下载、SSE 流。

**存量响应结构（不要动）**：

```json
{ "code": "200", "data": {}, "message": "成功" }
```

注意 `code` 是**字符串**（`"200"`/`"500"`/`"600"`），v2 是**数字**。前端按 URL 前缀分开解析，不要混用。

### 2.5 分页

**统一沿用存量字段名**（避免两套心智）：

```json
{ "pageNo": 1, "pageSize": 10, "count": 57, "list": [ ... ] }
```

| 参数 | 位置 | 默认 | 说明 |
|---|---|---|---|
| pageNo | query / body | 1 | 从 1 开始 |
| pageSize | query / body | 10 | 上限 50，超出按 50 处理 |
| orderBy | query | 空 | 只允许白名单字段，**禁止拼接 SQL** |

> 此前 `01` 号文档写的 `page/size/total/hasNext` 作废，以本章为准（`count` 即总数，`hasNext = pageNo*pageSize < count`）。

### 2.6 业务码表

| code | 含义 | 前端动作 |
|---|---|---|
| 200 | 成功 | 正常渲染 |
| 400 | 参数错误（缺字段、类型错、超长） | Toast 提示 message |
| **600** | 未登录 / token 过期 | 清 token → 跳登录 |
| 403 | 无权限（越权、角色不符） | Toast "无权操作" |
| 404 | 资源不存在 | Toast + 返回列表 |
| 409 | 冲突（重复提交、状态非法、抢单失败） | Toast + 刷新详情 |
| 422 | 业务校验不通过（评价窗口外、租约未结束） | Toast，**不刷新** |
| 429 | 频繁操作 | 提示稍后再试 |
| 500 | 服务器错误 | Toast "服务异常" + 展示 traceId |

> 新增码必须登记到本表，**禁止各模块自定义 4001/5001 这类私有码**。

### 2.7 数据格式

| 类型 | 约定 | 反例 |
|---|---|---|
| 时间 | ISO-8601 带时区 `2026-11-03T14:30:00+08:00` | `2026-11-03 14:30`（无时区，跨端会差 8 小时） |
| 日期 | `2026-11-03` | `2026/11/03` |
| **金额** | **整数，单位"分"**：`258000` = ¥2580.00 | `2580.5`（浮点必出错） |
| ID | 字符串，32 位 | 数字（JS 精度丢失） |
| 枚举 | **大写字符串** `CREATED` | 数字 `0/1/2`（v2 禁止） |
| 图片 | 完整 URL 或相对路径 `/uploads/xx.jpg`；列表用缩略图 `?w=400` | 前端拼接域名 |
| 布尔 | `true/false` | `1/0`（v2 禁止） |
| 空值 | 返回 `null` 或省略字段，**不返回 `"null"` 字符串** | `"null"` |

**金额换算只允许一处**：后端 `MoneyUtils.fen(BigDecimal)` / `MoneyUtils.yuan(long)`，前端 `(fen/100).toFixed(2)`。禁止在业务代码里到处 `*100`。

### 2.8 幂等与并发

| 场景 | 机制 |
|---|---|
| 重复提交（报修、评价、账单标记） | 客户端带 `Idempotency-Key: <uuid>`；服务端 24h 内同 key 返回首次结果 |
| 状态流转（接单、确认） | 乐观锁 `version`；失败返回 **409** |
| 唯一性（同租约只能评一次） | DB 唯一索引 `(reviewer_id, target_type, target_id, lease_id)` |

### 2.9 超时与重试

| 接口 | 客户端超时 | 是否重试 |
|---|---|---|
| 普通查询 | 10s | 可重试 1 次（幂等） |
| 写操作 | 15s | **不自动重试**（避免重复提交） |
| 上传 | 60s | 不重试 |
| AI 推荐 | **20s** | 不重试（降级返回即可） |

---

## 3. 存量接口清单（103 个，回归基线，不可改）

> 这张表就是 QA 的快照基线。`get / findPage / findAll / save / delete` 是标准 CRUD，可用同一份脚本批量取快照。
> 构成：12 个模块各含 5 个标准 CRUD（60 个）+ 扩展动作 28 个 + `sysCommon` 6 个 + `wechat` 9 个 = **103 个**。
> （`sysCommon` 与 `wechat` 没有标准 CRUD，所以不是 13×5。）

| 模块（前缀） | 接口 | 数量 |
|---|---|---|
| `user` | register, get, findPage, getLoginData, findAll, save, wechatSave, delete, uploadHead, **login**, loginJson, getBaseInfoCount | 12 |
| `houseResource` | get, findPage, findAll, save, delete, uploadPic, findByRole, findViewedHousePage, findCollectionHousePage, findRentingHousePage | 10 |
| `applyRent` | get, wechatGet, findPage, findAll, save, delete, findPersonalRentApply, paySuccess, getByEntity | 9 |
| `wechat` | **login**, loginByOpenId, getUserNumber, findHousePage, findHouseByCondition, getHouseById, findAllTags, getUserById, getPic | 9 |
| `applyView` | get, wechatGet, findPage, findAll, save, delete, findPersonalViewApply, getByEntity | 8 |
| `menu` | get, findPage, findAll, save, delete, findRoleMenu, keepRoleMenu, getLoginMenu | 8 |
| `dict` | get, findPage, findAll, findAllJson, save, delete, findAllType | 7 |
| `message` | get, findPage, findAll, save, delete, getNewCount, findPersonalMessage | 7 |
| `pic` | get, findPage, findAll, save, delete, uploadPic, findAllJson | 7 |
| `sysCommon` | getBaseUrl, getBaseInfo, getSystemResource, findLoginName, register, getDataCount | 6 |
| `collection` | get, findPage, findAll, save, delete | 5 |
| `houseRenting` | get, findPage, findAll, save, delete | 5 |
| `rent` | get, findPage, findAll, save, delete | 5 |
| `tag` | get, findPage, findAll, save, delete | 5 |

**存量接口的已知不一致（照抄现状，不要顺手"修好"）**：

| 现象 | 影响 | 处理 |
|---|---|---|
| `code` 是字符串 `"200"` | 与 v2 数字码不同 | 前端按前缀分流 |
| `login` 是表单参数（非 JSON），`loginJson` 是 JSON | 容易调错 | 新代码一律用 `loginJson` 或 v2 |
| 大量接口用 `@RequestMapping`（GET/POST 都能进） | 语义混乱 | 不改，v2 新接口严格 GET/POST |
| 分页字段 `pageNo/pageSize/count/list` | 与 v2 一致（好） | 全线沿用 |
| 上传返回 `visitPath` 字符串 | 结构不稳定 | v2 上传返回对象 |

---

## 4. 新增接口契约（v2）

公共头：`token: <jwt>`；写操作建议带 `Idempotency-Key`。

### 4.1 认证（沿用存量，不重做）

| 接口 | 说明 |
|---|---|
| `POST /user/loginJson` | Web 登录，JSON body；**新前端一律用这个** |
| `POST /wechat/login` | 小程序 `code` 换 openid + 签发 token |
| `POST /wechat/loginByOpenId` | 已有 openid 直接登录 |

响应（存量结构，字符串 code）：`{ "code":"200", "data":{"token":"...","loginUser":{...}}, "message":"登录成功" }`

### 4.2 评分 `/api/v2/rating`

**① 提交评价** `POST /api/v2/rating/submit`

```json
{
  "leaseId": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
  "targetType": "HOUSE",
  "targetId": "h1h2h3h4h5h6h1h2h3h4h5h6h1h2h3h4",
  "score": 4.5,
  "dimensions": { "decoration": 4, "location": 5, "costPerformance": 4, "landlordCoop": 4, "noise": 3 },
  "content": "采光很好，离地铁 8 分钟，晚上有点吵。",
  "images": ["/uploads/r/2026/11/xx1.jpg"]
}
```

约束：`score` 1–5，保留 1 位小数；`images` ≤ 3 张；`content` ≤ 500 字。
校验：租约 `status=TERMINATED` 且 `NOW() <= actual_end_date + 30 天`，否则 **422**；同租约重复提交 **409**。

响应：`{ "code":200, "data":{ "reviewId":"...", "status":"PUBLISHED" }, "traceId":"..." }`

**② 查房源评分** `GET /api/v2/rating/house/{houseId}`

```json
{
  "code": 200,
  "data": {
    "officialScore": 4.6,
    "userScore": 4.2,
    "displayScore": 4.31,
    "reviewCount": 8,
    "isNew": false,
    "dimensions": { "decoration": 4.3, "location": 4.7, "costPerformance": 4.0, "landlordCoop": 4.5, "noise": 3.6 },
    "reviews": [ { "id":"...", "userName":"张**", "score":5, "content":"...", "images":[], "reply":"感谢", "createdAt":"2026-11-20T10:00:00+08:00" } ]
  },
  "traceId": "..."
}
```

`isNew = reviewCount < 3`（前端据此显示"新上房源"，不显示低分）。

**③ 查房东评分** `GET /api/v2/rating/landlord/{landlordId}` — 结构同上，维度为 `{responseSpeed, repairSpeed, depositRefund, communication}`。

**④ 房东回复** `POST /api/v2/rating/{id}/reply` `{ "content":"感谢反馈，噪音问题已加装隔音条" }`（每条一次，≤100 字）

**⑤ 申诉** `POST /api/v2/rating/{id}/appeal` `{ "reason":"..." }` → 状态 `APPEALING`，申诉期间不计入均分。

**⑥ 平台核验（管理员）** `POST /api/v2/rating/audit`

```json
{ "houseId":"...", "items":{ "photoReal":true, "addressVerified":true, "facilityMatch":true, "priceReasonable":true, "contractUploaded":true, "safetyChecked":false, "depositRuleClear":true, "noComplaint":true } }
```
→ 返回 `{ "officialScore": 4.75 }`（Σ权重 × 5）

### 4.3 租约 `/api/v2/lease`

| 接口 | 说明 |
|---|---|
| `GET /api/v2/lease/{id}` | 详情（含 status、startDate、endDate、actualEndDate、moneyFen） |
| `GET /api/v2/lease/mine?status=ACTIVE` | 我的租约（租客/房东均可，按角色过滤） |
| `POST /api/v2/lease/{id}/terminate` | 退租确认，**评价窗口的触发器** |

`terminate` 请求：`{ "actualEndDate":"2026-11-30", "reason":"工作调动" }`
规则：房东或租客任一方可发起；`status` 由 `ACTIVE` → `TERMINATED`；写 `lease_log`；重复确认 **409**。

### 4.4 报修 `/api/v2/repair`

状态机：`CREATED → ACCEPTED → ON_SITE → COMPLETED → ACCEPTED_BY_TENANT`，任意非终态可 `→ CANCELLED`。

| 接口 | 请求 | 说明 |
|---|---|---|
| `POST /api/v2/repair` | `{houseId, leaseId, type, description, images[], expectTime}` | type: `WATER/ELECTRIC/DOOR_WINDOW/APPLIANCE/OTHER` |
| `GET /api/v2/repair/mine` | `?status=&pageNo=&pageSize=` | 租客看自己的，房东看自己房子的 |
| `GET /api/v2/repair/{id}` | — | 含 `logs[]` 流转时间线 |
| `POST /api/v2/repair/{id}/accept` | `{}` | 并发抢单：`version` 乐观锁，失败 **409** |
| `POST /api/v2/repair/{id}/arrive` | `{}` | |
| `POST /api/v2/repair/{id}/complete` | `{remark, images[]}` | |
| `POST /api/v2/repair/{id}/confirm` | `{score?, comment?}` | 租客验收，终态 |
| `POST /api/v2/repair/{id}/cancel` | `{reason}` | 仅 CREATED 可取消 |

非法跳转（如 `CREATED → COMPLETED`）返回 **409**，message 写明"当前状态不允许该操作"。

### 4.5 账单 `/api/v2/bill`

账单状态：`UNPAID → MARKED → PAID`，逾期为 `OVERDUE`（由 `payableDate` 计算，不落库或每日刷新）。

| 接口 | 请求 | 说明 |
|---|---|---|
| `POST /api/v2/bill/generate` | `{rentingId, period:"2026-11"}` | 房东手动生成；`(renting_id, period)` 唯一，重复 **409** |
| `GET /api/v2/bill/mine` | `?period=&status=` | 租客看自己的 |
| `GET /api/v2/bill/{id}` | — | 含 `items[]` 明细 |
| `POST /api/v2/bill/{id}/mark-paid` | `{channel:"offline_wechat", paidFen:258000, voucherPic:"/uploads/..."}` | 租客标记，`UNPAID→MARKED` |
| `POST /api/v2/bill/{id}/confirm` | `{}` | 房东确认，`MARKED→PAID`；重复确认 **200 但不累加** |
| `GET /api/v2/bill/pending` | `?pageNo=&pageSize=` | 房东待确认队列 |

账单详情示例：

```json
{
  "id":"...", "period":"2026-11", "status":"UNPAID",
  "amountFen": 258000, "paidFen": 0, "payableDate":"2026-11-05",
  "items":[
    {"itemType":"RENT","amountFen":230000,"remark":"月租"},
    {"itemType":"WATER","amountFen":8000,"remark":"12 吨"},
    {"itemType":"ELECTRIC","amountFen":15000,"remark":"150 度"},
    {"itemType":"PROPERTY","amountFen":5000,"remark":"物业"}
  ]
}
```

校验：`SUM(items.amountFen) == amountFen`，不等则 **500 + 日志**（数据错误，不能静默）。

### 4.6 AI 推荐 `/api/v2/ai`

**① 推荐** `POST /api/v2/ai/recommend`

```json
{ "query":"天河区 2500 以内两房 能养猫", "filters":{ "region":"天河", "budgetMax":250000, "rooms":2, "tags":["pet","metro"] }, "pageNo":1, "pageSize":10 }
```

```json
{
  "code": 200,
  "data": {
    "mode": "rule",
    "intent": { "region":"天河", "budgetMax":250000, "rooms":2, "tags":["pet","metro"], "source":"rule" },
    "list": [ { "houseId":"...", "title":"...", "priceFen":240000, "score":0.87, "hitTags":["pet","metro"], "reason":"天河两房，2400 元，命中"养宠""近地铁"，房东分 4.6" } ],
    "total": 12
  },
  "traceId": "..."
}
```

规则：
- `mode` 取 `rule` 或 `llm`，前端不判断，仅日志上报。
- **模型不可用时必须仍返回 200**，`mode=rule`，`reason` 用模板生成。
- 响应时间：结构化结果 <1s；`mode=llm` 时 `reason` 可为异步补充（另开接口），不阻塞列表。
- 无结果时返回空 `list` + `message:"没有完全匹配的房源，已为你放宽预算 20%"`（放宽策略后端做，前端只展示）。

**② 理由补全（可选，LLM 模式）** `POST /api/v2/ai/reason` `{ "houseIds":["...","..."] }` → `{ "items":[{"houseId":"...","reason":"..."}] }`，超时 5s 返回空数组，前端保留模板文案。

**③ 反馈埋点** `POST /api/v2/ai/feedback` `{ "houseId":"...", "action":"CLICK" }`（`IMPRESSION/CLICK/APPLY`），用于评测集校准。

### 4.7 文件 `/api/v2/file`

`POST /api/v2/file/upload`（multipart/form-data，字段名 `file`）

```json
{ "code":200, "data":{ "id":"...", "url":"/uploads/2026/11/xx.jpg", "thumbUrl":"/uploads/2026/11/xx_400.jpg", "size":184320, "width":1600, "height":1200 }, "traceId":"..." }
```

限制：仅 `jpg/jpeg/png/webp`；≤5MB；服务端压缩到 200KB 内并生成 400px 缩略图；文件名重命名为 UUID（防路径穿越）；**禁止**按原始文件名落盘。

---

## 5. 后端实现要求（给 BE 的硬约束）

| # | 要求 |
|---|---|
| 1 | 统一异常处理 `@RestControllerAdvice`：业务异常 → 对应 code；未捕获异常 → 500 + traceId，**不把堆栈返回前端** |
| 2 | 每个接口第一行校验 `owner_id`；越权返回 **403**，不返回 404（避免"猜 ID"探测） |
| 3 | 所有写接口必须能重复调用而不产生脏数据（幂等或唯一索引兜底） |
| 4 | 禁止用 `${}` 拼接 SQL；`LIKE` 用 `CONCAT('%', #{q}, '%')` |
| 5 | 时间统一 `LocalDateTime` + `Asia/Shanghai`；DB 连接串必须带 `serverTimezone=Asia/Shanghai` |
| 6 | 金额入参/出参一律 `long fen`；内部计算用 `BigDecimal`，禁止 `double` |
| 7 | 分页 `pageSize` 上限 50，超过按 50（防拖库） |
| 8 | 所有接口打点：`traceId` + 耗时 + code（供 Prometheus 统计错误率） |
| 9 | 日志不打明文手机号/密码/token（脱敏：`138****0001`） |
| 10 | v2 控制器统一放 `com.wt.hrentSystem.api.v2` 包，与存量 controller 物理隔离 |

---

## 6. 前端 / 小程序对接规范

### 6.1 Web（axios 封装，四件事）

```js
// 1) 注入 token   2) 统一解包 code   3) 600 跳登录   4) 请求去重
service.interceptors.request.use(cfg => { cfg.headers.token = getToken(); return dedupe(cfg); });
service.interceptors.response.use(res => {
  const { code, message, data, traceId } = res.data;
  if (code === 200 || code === '200') return data;
  if (code === 600 || code === '600') { clearToken(); router.push('/login'); }
  return Promise.reject(Object.assign(new Error(message), { code, traceId }));
});
```

**必须做**：300ms 内同 URL + 同参数的请求合并（首页三个组件同时请求房源列表会打三次）。

### 6.2 小程序（`wx.request` 封装）

- token 存 `wx.setStorageSync('token')`，请求头注入 `token`。
- 超时按 2.9 表设置；AI 推荐单独 20s。
- **图片一律用相对路径/后端返回的 `thumbUrl`**：`<image src="{{item.thumbUrl}}">`，不要在前端拼域名（预览模式下图片域名也受校验，拼错就全是灰块）。
- 列表分页：`pageNo` 从 1 开始，`onReachBottom` 时 +1；`count <= pageNo*pageSize` 时置 `noMore`。

### 6.3 类型生成（禁止手写）

```bash
# W2 spec 冻结后执行，字段变更后重跑
pnpm dlx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts
```

### 6.4 状态与枚举映射（前端不要自己造）

| 域 | 枚举 |
|---|---|
| 租约 | `ACTIVE` / `EXPIRED` / `TERMINATED` / `CANCELLED` |
| 报修 | `CREATED` / `ACCEPTED` / `ON_SITE` / `COMPLETED` / `ACCEPTED_BY_TENANT` / `CANCELLED` |
| 账单 | `UNPAID` / `MARKED` / `PAID` / `OVERDUE` |
| 评价 | `PUBLISHED` / `APPEALING` / `HIDDEN` |
| 评价对象 | `HOUSE` / `LANDLORD` |

> 存量是数字枚举（`state: 0/1/2`），v2 是字符串；同一页面混用两套时，在 `constants/` 里做一次映射，不要在模板里写魔法数字。

---

## 7. 接口自查清单（每个接口提测前逐条打勾）

| # | 检查项 |
|---|---|
| 1 | 路径带 `/api/v2` 前缀（新接口） |
| 2 | 返回 `{code,message,data,traceId}` 四字段齐全 |
| 3 | 成功/参数错/未登录/越权/不存在/冲突 六种情况都手测过 |
| 4 | 分页返回 `pageNo/pageSize/count/list` |
| 5 | 金额是"分"的整数，不是浮点 |
| 6 | 时间是带时区的 ISO-8601 |
| 7 | 越权：换另一个账号的 id 访问，返回 403 |
| 8 | 重复提交：同参数连点两次，不产生两条数据 |
| 9 | 空结果：返回 `list: []`，不是 `null` |
| 10 | 大分页：`pageSize=1000` 被截到 50 |
| 11 | 异常：故意传错类型，返回 400 而非 500 |
| 12 | 已登记到 OpenAPI，且 `openapi-typescript` 能生成 |

---

## 8. Mock 与联调流程

```
W2 周三   BE 输出全量 spec（存量 + v2）
W2 周四   PM 组织 spec 评审（FE/MP/QA 各提 3 个问题，当场改）
W2 周五   冻结 v1.0，导入 Apifox → 起 Prism mock
W3 起     FE/MP 按 mock 开发（mock 返回固定样例，含空列表/错误码两种）
W5 起     真接口替换 baseURL，只跑冒烟，不改代码结构
```

**Mock 必须覆盖三种响应**：正常数据、空列表、`code=600`（验证登录跳转）。

---

## 9. 变更流程（冻结后）

| 变更类型 | 流程 | 版本 |
|---|---|---|
| 加字段（可选） | 群里 @ 消费方 + 更新 spec | v1.0.x |
| 改字段名/类型 | 提"接口需求单" → PM 批 → BE 改 → 双端同步 | v1.1.0 |
| 删字段/改路径 | **禁止**（本期）；确需则新增接口 + 旧接口标记 `@Deprecated` | v2.0.0 |
| 错误码新增 | 登记到 2.6 表后才可用 | — |

**红线**：不允许"我先改了你跟上"；不允许口头约定字段；不允许前端按返回值的"有没有这个字段"来判断版本。

---

## 10. 与既有文档的修正关系

| 文档 | 原写法 | 本规范修正 |
|---|---|---|
| `01` §五 | 分页 `page/size` + `total/hasNext` | 改为 `pageNo/pageSize/count/list` |
| `01` §五 | 金额"分"（未说存量怎么办） | 明确：存量 DOUBLE 元只读，接口层一律分 |
| `06` W2 | "40+ 接口快照" | **实测 103 个**，脚本批量取（13 模块 × 5 通用 CRUD 同模板） |
| `00` 验收 #5 | "非法跳转返回 4xx" | 改为"业务码 409"（HTTP 恒 200） |
| `03` §3.2 | 未定义错误码 | 以 2.6 表为准 |

> 接口数从"40+"修正为 **103** 是本次实测结果。QA 的快照工作量不变（通用 CRUD 用脚本批量），但**用例覆盖面变大**，W2 要按模块分优先级：先核心链路 30 条，再补全量。
