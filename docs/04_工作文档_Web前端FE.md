# 04 · 个人工作文档 v2.2 · Web 前端开发（FE）

> **评审分目标：C 7.0（每周五对分）**

> 成员：FE（1 人）
> 周期：2026-11-03 ~ 2027-01-23
> **范围：只重写 4 页（登录 / 房源列表 / 房源详情 / 数据大屏），其余 8 个 view 保留 Element UI 换主题变量。**
> **v2.1 修正：view 数量（实际 12 个，不是 10 个）、tokens 单一来源、openapi-typescript 自动生成类型、首屏指标改 500KB。**

---

## 一、技术基线

| 项 | 版本 / 选择 |
|---|---|
| 框架 | Vue 3.4 + TypeScript + Vite 5 |
| 状态 | Pinia |
| UI | Element Plus 2.x（**不引暗色模式**） |
| 图表 | ECharts 5（不引高德，热力图砍） |
| HTTP | axios 封装：自动带 JWT、解析 `code`、挂 `traceId`、**请求去重** |
| 类型 | `openapi-typescript` 从 W2 的 spec 自动生成 `types/api.d.ts` |
| 包管理 | pnpm |

---

## 二、为什么只重写 4 页（数字修正）

`hrent-web/src` 实测：**25 个文件、12 个 view**（不是 v2 写的 10 个）。

| 页面 | 处理 | 理由 |
|---|---|---|
| Login / HouseManage（房源列表）/ HouseDetail / ShowData（大屏） | **重写** | 老板 90% 观感来自这 4 页 |
| Init / MenuManage / PicManage / UserManage / DictManage / HouseRenting / RentRecord / LookHouseApply / RentHouseApply | **保留结构，换主题变量** | 内部管理页，重写没有业务价值 |

全量重写 8 个管理页 ≈ 7 人日，收益接近 0；这 7 人日应该给"大屏"和"错误态/空态"。

---

## 三、周度任务（v2.1）

| 周 | 日期 | 任务 | 交付物 |
|---|---|---|---|
| W1 | 11/03-11/09 | Vite + Vue3 + TS 脚手架；Pinia/Router/axios；**只写登录页逻辑，不写样式**；▲**验证图片走相对路径（避开小程序图片域名校验）** | 工程能起，登录能拿 token，图片验证结论 |
| W2 | 11/10-11/16 | Element Plus 接入；布局组件；**消费 `tokens.json` 生成的 tokens.css**；**接 `openapi-typescript` + mock server**；▲**Playwright 5 条冒烟（算支援 QA 的 2 人日）** | 布局 + 类型 + mock + E2E 四通 |
| W3 | 11/17-11/23 | 房源列表页（新 UI）：搜索、表格、分页、详情抽屉 | 列表页 |
| W4 | 11/24-11/30 | 登录页 + 房源详情页；**其余 8 个 view 换肤，硬封顶 2 人日**（只动 CSS 变量 + 表格 `size="small"` + 行高 + 按钮间距，**不动结构、不改布局**） | P1 出口 |
| W5 | 12/01-12/07 | 评分组件：雷达图 + 官方/用户双柱 + 评价列表 | 评分卡 |
| W6 | 12/08-12/14 | 报修工单：列表、状态筛选、详情抽屉、流转时间线 | 工单页 |
| W7 | 12/15-12/21 | 账单看板：账单列表、按月生成、详情、**待确认队列** | 账单页 |
| W8 | 12/22-12/28 | AI 推荐卡片：标签、理由（**打字机**）、跳详情 | AI 卡 |
| W9 | 12/29-01/04 | **数据大屏**：空置率趋势、租金走势、报修漏斗、评分分布（ECharts，不做热力图） | 大屏页 |
| W10 | 01/05-01/11 | 性能：路由懒加载、ECharts 按需引入、gzip、首屏 **<500KB** | Lighthouse >85 |
| W11 | 01/12-01/18 | UI 走查（4 页对齐 Figma）+ **空态/加载/错误态补齐** | 走查清单 |
| W12 | 01/19-01/23 | 打包 + Nginx 配置（`gzip on`、`try_files`） | dist/ |

---

## 四、必须遵守的实现细节（v2.1 新增）

### 4.1 类型从 spec 生成，不手写

```bash
pnpm dlx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts
```
- W2 spec 冻结后执行一次；字段变更后重跑。
- 组件里只用生成类型，不写 `any`。
- 收益：接口 breaking change 在编译期就报错，比"红线不许 any"有效。

### 4.2 主题只走 tokens，不写死色值

- `design/tokens.json` 是唯一来源，脚本生成 `src/styles/tokens.css`（`--brand-primary` 等）。
- Element Plus 的主题用 `--el-color-primary` 覆盖，**不 fork 组件、不写 `!important`**。
- 8 个保留页只换变量 + 修间距，不改结构。

### 4.3 axios 封装必须有四件事

| 能力 | 说明 |
|---|---|
| JWT 注入 | 请求头 `Authorization: Bearer` |
| 统一错误 | `code != 0` → Toast；401 → 跳登录并清 token |
| traceId | 响应头回传的 `traceId` 存到 error 对象，报错时可复制 |
| **请求去重** | 同一 URL + 参数 300ms 内合并，避免首页三个组件同时打房源列表 |

### 4.4 三态必须写

每个列表/详情都要有：**loading（骨架屏）/ empty（空状态插画 + 引导）/ error（重试按钮）**。
这是"简陋空洞"最直接的来源之一——老系统点了没反应、空了是白屏。

---

## 五、设计规范

| 项 | 值 |
|---|---|
| 主色 | #0B6EF8 |
| 辅助 / 强调 | #FF9500（评分星标） |
| 成功 / 警告 / 危险 | #22C55E / #F59E0B / #EF4444 |
| 正文 | 14px / #1F2937 |
| 卡片圆角 | 8px |
| 页面间距 | 24px |
| **砍掉** | 暗色模式、1366/1920 双断点（只做 ≥1280 弹性栅格）、高德热力图 |

---

## 六、红线

- 不允许在 Figma 冻结（W2 末）前写样式。
- 不允许引入 Vue2 生态（element-ui / vuex）。
- 不允许手写接口类型（用 `openapi-typescript`）；不允许 `any`。
- 不允许直接调后端地址，必须走 axios 封装。
- 不允许页面出现"点了没反应"（必须有 loading / 失败提示）。
- 首屏 >500KB 或 >3s 视为未完成（W10 判定）。
