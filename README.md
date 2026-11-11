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

## 四、页面截图
### 1、管理员页面
![contents](./picture/picture0.png)
![contents](./picture/picture00.png)
![contents](./picture/picture1.png)
![contents](./picture/picture2.png)
![contents](./picture/picture3.png)
![contents](./picture/picture4.png)
![contents](./picture/picture5.png)
![contents](./picture/picture6.png)
![contents](./picture/picture7.png)
![contents](./picture/picture8.png)
![contents](./picture/picture9.png)
![contents](./picture/picture10.png)
![contents](./picture/picture11.png)
![contents](./picture/picture12.png)
![contents](./picture/picture13.png)
### 2、房东页面
![contents](./picture/picture14.png)
![contents](./picture/picture15.png)
![contents](./picture/picture16.png)
![contents](./picture/picture17.png)
![contents](./picture/picture18.png)
### 3、用户页面
![contents](./picture/picture19.png)
![contents](./picture/picture20.png)
![contents](./picture/picture21.png)
![contents](./picture/picture22.png)
![contents](./picture/picture23.png)
![contents](./picture/picture24.png)
![contents](./picture/picture25.png)
![contents](./picture/picture26.png)


## 五、浏览地址
- http://localhost:8080/

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

### 7.3 一键启动（推荐，跨平台）

> **不需要预装 Maven**：项目自带 Maven Wrapper（固定 Maven 3.9.16），`./mvnw` / `mvnw.cmd` 会自动下载对应版本。

```bash
# 1) 首次：复制环境变量模板，填写数据库口令与 JWT 密钥
cp .env.example .env          # Windows 用：copy .env.example .env

# 2) 一键启动（自动判断是否需构建 → 后台启动 → 健康检查）
./start.sh                    # Linux / macOS / WSL / Git Bash
start.cmd                     # Windows

# 3) 常用命令
./start.sh status             # 运行状态 + 健康检查
./start.sh logs -f            # 跟踪日志（Ctrl+C 退出）
./start.sh stop               # 停止（含前端）
./start.sh restart            # 重启
./start.sh build              # 仅构建后端
./start.sh build-web          # 仅构建前端到 hrent-web/dist
./start.sh dev                # 后端 + 前端开发服务器（热更新）
./start.sh help               # 全部命令
```

**前端如何一起起来**——两种模式，按用途选：

| 用途 | 命令 | 访问地址 | 需要 Node |
|---|---|---|---|
| 生产 / 演示（**单进程**） | `build-web` 构建一次 → `start` | `http://localhost:8888/hrent/web/index.html` | 仅构建时 |
| 开发（热更新） | `dev` | `http://localhost:8080/` | 是 |

后端启动时若 `hrent-web/dist` 存在，会通过 `HRENT_WEB_DIR` 把它映射到 `/hrent/web/**`（见 `WebMvcConfig`），**一个 Java 进程同时提供页面与接口**，服务器上不需要额外装 nginx。前端为 hash 路由 + `assetsPublicPath: './'`，放在子路径下可直接工作，不需要服务端 fallback。

`dev` 模式会先等后端健康检查通过，再起 webpack-dev-server；前端统一用相对路径 `/hrent/**` 请求接口，由 `config/index.js` 的 `proxyTable` 转发，代码中不再有写死的 host/port。

脚本自动处理平台差异：JDK 版本分支（JDK 9+ 才加 `--add-opens`，JDK 8 加会启动失败）、Windows 用 `taskkill` 停进程、日志轮转（`logs/hrent.log` + `.log.1`）、端口占用检测、重复启动保护。
完整参数、环境变量与服务器部署见 **[`docs/24_一键启动与部署指南.md`](docs/24_一键启动与部署指南.md)**。

**Windows 直接双击 `start.cmd` 即可**：窗口会保留并显示结果——成功给出访问地址，失败给出原因与日志路径，不会再一闪而过；服务已在运行时重复双击按「已启动」处理，不算失败。自动化 / CI 里设 `HRENT_NO_PAUSE=1` 可关闭窗口停留。

> 维护约定：`start.cmd` 必须保持**纯 ASCII + CRLF**。cmd.exe 按控制台代码页（中文 Windows 为 GBK）解析批处理，中文注释会乱码并被拆成命令执行——这正是历史版本双击闪退的根因。中文提示一律写在 `scripts/hrent.ps1`（UTF-8 BOM）。

### 7.4 手动启动（不用脚本时）

1. 后端构建：`cd hrent-api && ./mvnw clean package`（跳过测试加 `-DskipTests`）。
   - 若本机 `~/.m2/settings.xml` 把 `localRepository` 写死成别人的绝对路径，改用项目模板：`./mvnw -s .mvn/settings.xml clean package`。
   - 首次运行需联网下载 Maven 发行版与依赖；完全离线机器需自备依赖包（见 `docs/23` H1）。
2. 后端启动：`java -jar target/hrent-0.0.1-SNAPSHOT.jar --server.port=8888`（JDK17 另需 `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED`）。服务实际为 `http://localhost:8888/hrent`（`application.yml` 的 `port` + `context-path`，与 `docs/13` 写的 8080 无 context-path 不一致，裁决见 `docs/23` T-01）。
3. 前端：`cd hrent-web && npm install && npm run dev`；构建时若用 Node 17+，需 `NODE_OPTIONS=--openssl-legacy-provider npm run build`。
4. 数据库：Flyway V1–V7 会自动校验/执行；新表 DDL 见 `hrent-api/src/main/resources/db/migration/`。

### 7.5 本次校验结果（2026-11-11 运行时闭环实测）

| 项 | 结果 |
|---|---|
| 后端构建 | **BUILD SUCCESS**（`./mvnw -s .mvn/settings.xml clean package`，3 个测试 0 失败，jar 29 MB） |
| 服务启动 | **通过**（8888 + `/hrent`，`Started in 21.155s`，Flyway 8 条迁移校验通过） |
| v2 接口用例 | **32 PASS / 0 FAIL**（详见 `docs/21` 第七章） |
| 老接口回归 | **0 回归**（103 个：实调 65 个读接口，写语义 38 个跳过；2 个存量死接口恒 500，无端调用） |
| 未放行项 | 38 个写接口未回归、v2 四页面未做浏览器联调、2 个存量死接口未修 |

> 以上为**真实执行结果**（非静态推断）：构建使用项目自带 `mvnw`，启动与接口验证在本机 8888 端口实际跑通。一键启动脚本另做平台兼容性实测，记录见 `docs/24` 第八章。

### 7.6 文档索引

| 文档 | 内容 |
|---|---|
| `docs/CHANGELOG.md` | 逐批次改动日志（B0–B5），读取时从末尾倒数几十行看最近进展 |
| `docs/19_待定事项清单.md` | 13 项待定问题、临时处置与闭环规则 |
| `docs/20_功能描述文档.md` | 模块、接口、页面、数据与安全设计 |
| `docs/21_测试文档.md` | 用例集、校验结果、未执行项与缺陷清单 |
| `docs/22_下一步需求文档.md` | 待定闭环、二期 backlog 与验收门槛 |
| `docs/23_P0决策与验证闭环.md` | 5 项 P0 裁决依据与 V1–V4 实测记录 |
| `docs/24_一键启动与部署指南.md` | 启动脚本命令、环境变量、服务器部署与排障 |

---

## 八、一键启动与部署

### 8.1 交付物

| 文件 | 作用 |
|---|---|
| `start.sh` | Linux / macOS / WSL / Git Bash 一键入口 |
| `start.cmd` | Windows 一键入口（转调 `scripts/hrent.ps1`） |
| `scripts/hrent.ps1` | Windows 侧实现（PowerShell 5.1+ 兼容） |
| `.env.example` | 环境变量模板（复制为 `.env` 后填写，`.env` 不入库） |
| `deploy/hrent.service` | systemd 服务模板 |
| `deploy/nginx.conf.example` | nginx 站点模板（静态前端 + 反代后端） |

### 8.2 三个必须知道的约定

1. **端口变量叫 `HRENT_PORT`，不叫 `SERVER_PORT`**。部分云平台/CI 会注入 `SERVER_PORT` 或 `PORT`，脚本若读它会被劫持（实测被改成 52051 导致启动失败）。脚本固定用 `--server.port` 显式覆盖。
2. **凭据只写 `.env`**，且 `.env` 已被 `.gitignore` 忽略。`.env.example` 里全是 `change-me` 占位符。
3. **JDK 8 与 9+ 的启动参数不同**：`--add-opens` 只有 JDK 9+ 认识，JDK 8 传了会直接报错退出。脚本按 `java -version` 自动分支。

### 8.3 服务器部署（三步）

```bash
# 1) 构建（服务器上装 JDK 17 即可，无需装 Maven）
cd /opt/hrent/hrent-api && ./mvnw -DskipTests clean package

# 2) 配环境变量并装 systemd 服务
sudo cp .env.example /opt/hrent/.env && sudo chmod 600 /opt/hrent/.env   # 编辑填真实口令
sudo cp deploy/hrent.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now hrent

# 3) 前端 + nginx
cd hrent-web && npm install && NODE_OPTIONS=--openssl-legacy-provider npm run build
sudo cp -r dist/* /opt/hrent/web/ && sudo cp deploy/nginx.conf.example /etc/nginx/conf.d/hrent.conf
sudo nginx -t && sudo systemctl reload nginx
```

详细步骤、参数说明与故障排查见 [`docs/24_一键启动与部署指南.md`](docs/24_一键启动与部署指南.md)。



