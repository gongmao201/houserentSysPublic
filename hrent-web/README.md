# hrent-web

> A Vue.js project

## Build Setup

``` bash
# install dependencies
npm install

# serve with hot reload at localhost:8080
npm run dev

# build for production with minification
npm run build

# build for production and view the bundle analyzer report
npm run build --report
```

For a detailed explanation on how things work, check out the [guide](http://vuejs-templates.github.io/webpack/) and [docs for vue-loader](http://vuejs.github.io/vue-loader).

---

## v2 页面（2026-11-11 新增）

| 文件 | 用途 |
|---|---|
| `src/network/v2.js` | v2 独立请求层：token 注入、`{code,message,data,traceId}` 解包、600 跳登录、300ms 请求去重、`fen2yuan`/`yuan2fen` |
| `src/view/v2/RatingManage.vue` | 评分概览、提交评价、回复/申诉、平台核验 |
| `src/view/v2/RepairManage.vue` | 工单列表、详情时间线、5 个状态动作、新建报修 |
| `src/view/v2/BillManage.vue` | 账单列表、明细对账、生成、标记已缴、确认到账、待确认队列 |
| `src/view/v2/AiRecommend.vue` | 自然语言 + 结构化条件推荐、理由卡片、点击埋点 |

路由见 `src/router/index.js` 的 4 条 children，菜单见 `src/components/common/MyMenu.vue` 的「新功能 v2」子菜单。**存量 11 个页面与 `network/core.js` 未改动**。

baseURL 取自 `src/config.js`（与存量一致，待定 T-01/T-08 要求二期改为环境变量注入）。

## 构建注意事项（Node 17+）

webpack 3 使用 OpenSSL MD4，在 Node 17+ 会报 `ERR_OSSL_EVP_UNSUPPORTED`，构建需要：

```bash
NODE_OPTIONS=--openssl-legacy-provider npm run build
```

本次在 Node 22.22.2 下用该命令构建成功（webpack 3.12，`app.js` 150 kB）。`dist/` 为可再生产物，每次构建会重新生成。

## 联调前提

后端需先启动并登录拿到 `token`（v2 接口除 AI 推荐/理由外都要求登录，未登录返回业务码 600，前端会清 token 跳登录）。服务实际地址为 `http://localhost:8888/hrent/api/v2`，详见根目录 `README.md` 第七章。
