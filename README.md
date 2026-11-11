> **公开版本说明**：本仓库是脱敏后的公开发布副本，不含任何真实密钥、访问令牌、账号口令、
> 连接配置、个人身份信息与业务隐私数据。配置中的密钥与连接串均为占位符，部署前请按
> [PUBLISH_NOTES.md](./PUBLISH_NOTES.md) 自行配置；截图、私有工作区日志、源码压缩包、
> Word 交付版与构建产物已排除。业务代码逻辑与原始工作副本一致。

<p><h1 align="center">基于Java+Springboot+Vue的房屋租赁系统小程序</h1></p>


## 一、系统介绍
本系统前后端分离

本系统分为用户、房东、超级管理员三种角色

### 1、用户：
登录、注册、房屋搜索、房屋收藏、看房预约、租房申请、租房记录、看房记录、收藏记录、我的消息、个人信息修改。
### 2、房东：
登录、注册、数据展示、房源列表、在租列表、看房申请管理、租房申请管理、消息查看、个人信息修改。
### 3、超级管理员：
包括房东所有功能、用户管理、图片管理。

## 二、所用技术
后端技术栈：
- Springboot
- SpringMvc
- mybatis
- mysql
- maven

前端技术栈：
- Vue
- elementui
- vue-router
- axios
- 微信小程序

## 三、环境介绍
基础环境 :IDEA/eclipse, JDK 1.8, Mysql5.7及以上, Node.js(14.21), Maven3.6, Vscode, 微信开发者工具

所有项目以及源代码本人均调试运行无问题 可支持远程调试运行


## 五、浏览地址
- http://localhost:8888/hrent

超级管理员的账号密码为：<DEMO_ACCOUNT>/<DEMO_PASSWORD>

房东账号密码为：lisi/<DEMO_PASSWORD>

## 六、安装教程

1. 使用Navicat或者其它工具，在mysql中创建对应名称的数据库，并执行项目的sql

2. 使用IDEA/Eclipse导入hrent-api项目，导入时，若为maven项目请选择maven; 等待依赖下载完成

3. 修改resources目录下面application.yml里面的数据库配置和文件路径配置，将wechat-webapp.properties配置文件中的appId和appSecret改为自己的。

4. com/wt/hrentSystem/HrentApplication.java启动后端项目

5. vscode或idea打开hrent-web项目

6. 在编译器中打开terminal，执行npm install 依赖下载完成后执行 npm run dev,执行成功后会显示前台访问地址

7. 微信开发者工具导入hrent-wechat项目,微信开发者工具会自动编译

---

## 七、v2 新功能（2026-11-11 开发迭代）

按 `docs/13_API接口规范.md` 补齐六组 v2 接口与管理端四个页面。**存量 103 个接口、11 个存量页面零改动**，新代码全部放在 `hrent-api/.../api/v2` 与 `hrent-web/src/view/v2`。

### 7.1 接口分组（统一前缀 `/api/v2`，HTTP 恒 200，业务码在 `code`）

| 组 | 路径 | 主要能力 |
|---|---|---|
| 评分 | `/api/v2/rating/**` | 提交评价（退租后 30 天窗口）、房源/房东评分、回复、申诉、平台核验 |
| 租约 | `/api/v2/lease/**` | 租约详情、我的租约、退租确认（评价窗口触发器） |
| 报修 | `/api/v2/repair/**` | 工单创建、列表、详情时间线、5 个状态动作 |
| 账单 | `/api/v2/bill/**` | 按月生成、我的账单、明细对账、标记已缴、确认到账、待确认队列 |
| AI | `/api/v2/ai/**` | 规则推荐（模型不可用仍 200）、理由模板、反馈埋点 |
| 文件 | `/api/v2/file/upload` | 图片白名单上传（≤5MB，UUID 重命名） |

统一响应体 `{code, message, data, traceId}`；请求头 `token: <jwt>`；分页 `pageNo/pageSize/count/list`；金额单位为**分**。完整契约见 `docs/13`，逐条用例见 `docs/21_测试文档.md`。

### 7.2 前端入口

登录管理端后，左侧菜单 **「新功能 v2」** 下有：评分管理 / 报修工单 / 账单管理 / AI 推荐。
前端 v2 请求层 `hrent-web/src/network/v2.js` 独立封装（token 注入、错误解包、600 跳登录、300ms 请求去重）。

### 7.3 启动方式

1. 后端：导入 `hrent-api`，按原教程配置数据库后启动 `HrentApplication`；服务实际为 `http://localhost:8888/hrent`（`application.yml` 的 `port` + `context-path`，与 `docs/13` 写的 8080 无 context-path 不一致，见待定 T-01）。
2. 前端：`cd hrent-web && npm install && npm run dev`；构建时若用 Node 17+，需 `NODE_OPTIONS=--openssl-legacy-provider npm run build`。
3. 数据库：Flyway V1–V7 会自动校验/执行；新表 DDL 见 `hrent-api/src/main/resources/db/migration/`。

### 7.4 本次校验结果

| 项 | 结果 |
|---|---|
| 后端全量源码编译 | 通过（`javac` + 本地 Maven 仓库 jar；`api` 包 24 个 class / 6 个 Controller） |
| Mapper XML 解析 | 全部通过 |
| 前端生产构建 | 通过（webpack 3.12，app.js 150 kB） |
| 运行时接口冒烟 | **未执行**（本环境无外网且 Maven 无法解析父 POM，见待定 T-10） |

> 注意：本机 `mvn` 不在 PATH 且沙箱无法访问 Maven 仓库，改用 `javac` 覆盖语法与类型校验；上线前务必在联网机器执行 `mvn clean package` + 启动冒烟。

### 7.5 文档索引

| 文档 | 内容 |
|---|---|
| `docs/CHANGELOG.md` | 逐批次改动日志（B0–B5），读取时从末尾倒数几十行看最近进展 |
| `docs/19_待定事项清单.md` | 13 项待定问题、临时处置与闭环规则 |
| `docs/20_功能描述文档.md` | 模块、接口、页面、数据与安全设计 |
| `docs/21_测试文档.md` | 用例集、校验结果、未执行项与缺陷清单 |
| `docs/22_下一步需求文档.md` | 待定闭环、二期 backlog 与验收门槛 |



