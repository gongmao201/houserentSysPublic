# 公开发布说明（PUBLISH NOTES）

> 本仓库是 **HouseRent 房屋租赁系统** 的公开发布副本，由本地工作副本经「白名单复制 + 逐项脱敏」生成，
> **不含任何真实密钥、访问令牌、账号口令、连接配置、个人身份信息或业务隐私数据**。
> 原始工作副本的业务代码逻辑未作改动；本副本中只替换了配置值、示例数据与文档表述。

---

## 一、需要自行配置的占位符

克隆后请按自己的环境替换以下占位符，**不要把这些占位符直接用于生产**：

| 占位符 | 出现位置 | 建议配置方式 |
|---|---|---|
| `<YOUR_WECHAT_APPID>` | `hrent-wechat/project.config.json`、旧版说明文档 | 微信公众平台小程序 AppID |
| `<YOUR_WECHAT_APPSECRET>` | 旧版说明文档示例 | 服务端环境变量，禁止写入前端 |
| `<YOUR_QQMAP_KEY>` | `hrent-web/src/config.js`、`hrent-web/index.html` | 腾讯地图 Key，走 `.env` / 构建变量注入 |
| `<SET_VIA_ENV_JWT_SECRET>` | `TokenUtils.java` 兜底默认值 | 改为读取 `JWT_SECRET` 环境变量 |
| `${DB_URL}` / `${DB_USERNAME}` / `${DB_PASSWORD}` | `hrent-api/src/main/resources/application.yml` | 环境变量；`DB_URL` 缺省为本地 MySQL 示例地址 |
| `${JWT_SECRET}` / `${WECHAT_APP_ID}` / `${WECHAT_APP_SECRET}` | `application.yml` | 环境变量 |
| `<DEMO_PASSWORD>` / `<DEMO_ACCOUNT>` | 文档与 SQL 注释中的演示账号描述 | 仅作说明，不是可用口令 |
| `00000000000000000000000000000000` | 演示数据的 password 字段 | 占位哈希；部署前用自己口令的 MD5/BCrypt 替换 |

`application.yml` 已改为环境变量优先（带本地默认值），示例：

```bash
export DB_URL='jdbc:mysql://localhost:3306/house_rent?...'
export DB_USERNAME='your_user'
export DB_PASSWORD='your_password'
export JWT_SECRET='a-long-random-secret'
```

> 演示种子数据（`db/seed/seed_dev.sql`、`sql/house_rent.sql`）中的口令哈希**已统一置为占位值**，
> 直接导入后演示账号无法登录；请生成本地演示口令哈希后再替换，或改用自己创建的账号。

---

## 二、已清理 / 已排除的内容类型

| 类型 | 处置 |
|---|---|
| 微信 AppID / AppSecret 明文 | 全部替换为占位符 |
| 腾讯地图 Key | 全部替换为占位符 |
| JWT 签名密钥硬编码值 | 替换为占位符 + 环境变量外部化 |
| 数据库口令、用户名、连接串 | 环境变量占位；本机内网 IP 改为本地回环 |
| 默认演示口令明文（登录表单、文档、curl 示例、注释） | 全部替换为占位符或清空 |
| 演示账号口令哈希 | 统一置为占位哈希 |
| 个人手机号 / 邮箱 / 姓名（示例数据） | 手机号改掩码、邮箱改 `.invalid` 保留域、姓名改中性演示标识 |
| 作者联系方式（手机号 / 微信号 / QQ / QQ 群） | 整行删除 |
| 第三方源码站推广与商业服务描述 | 删除 |
| 本机绝对路径（`C:\Users\...`、`F:\...`、备份路径） | 替换为 `<LOCAL_PATH>` / `<PROJECT_ROOT>/` 占位 |
| 内网 IP | 替换为 `127.0.0.1` |
| 页面截图配图章节 | 删除（对应的截图资源一并排除） |

**整体排除、未上传的目录/文件**：

- `.git/`（原本地仓库历史）、`.workbuddy/`（私有工作区记忆与日志）
- `*.rar` 源码压缩包（3 个，内容未脱敏且体积大）、`*.docx`（Word 交付版，含文档元数据）
- `picture/`、`output/`、`docs/_work/`（系统截图、渲染中间产物、含绝对路径的 HTML）
- `node_modules/`、`target/`、`hrent-web/dist/`（依赖与构建产物）
- 重复的旧版说明 `README2.md`、`hrent-api/README2.md`（含明文密钥与本机路径）

> 保留 `hrent-wechat/dist/`：它是小程序引用的开源 UI 组件（iView Weapp / WeUI）构建产物，属于运行所需资源；
> 保留各目录下的 `*.png` / `*.jpg`：均为程序图标、默认头像与按钮素材，不是业务截图。

---

## 三、提交历史

目标仓库在原有 `Initial commit`（仅含 `LICENSE`）基础上追加本次发布提交；
**原本地仓库那次包含未脱敏文件的提交历史并未推送**，因此公开仓库的全部历史中都不存在敏感内容。
发布提交的作者身份使用中性标识，不含个人邮箱。

---

## 四、验证口径

发布前对副本全量文本文件执行了两轮正则扫描，覆盖：密钥/令牌、手机号、邮箱、证件号、
本机绝对路径、内网 IP、明文口令、口令哈希、作者个人信息、示例姓名、排除项残留。
最终扫描结果：**870 个文件，命中 0 项**。

---

## 五、版权与授权提示

本项目原始代码来自第三方分发的开源教学/示例源码包，并在其基础上做了现代化改造与 v2 功能扩展。
公开发布前请确认原始分发许可允许再分发与署名要求；如涉及第三方图片素材、UI 组件，
请按其各自许可证使用。本仓库的 `LICENSE` 仅覆盖仓库所有者新增的部分。
