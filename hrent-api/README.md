## 基于Java+Springboot+Vue的房屋租赁系统小程序(源码+数据库)110

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

## 七、v2 接口层（2026-11-11 新增，存量零改动）

### 7.1 目录结构

```
src/main/java/com/wt/hrentSystem/api/
├── dao/V2Mapper.java                 # v2 专用查询/流转 SQL（复用已有 @MapperScan）
└── v2/
    ├── ApiCodes / ApiResponse / ApiException / ApiExceptionHandler
    ├── V2TraceFilter / TraceIds / V2Context / Req / Rows / PageResult / V2Time
    ├── RatingService + RatingController
    ├── LeaseService  + LeaseController
    ├── RepairService + RepairController
    ├── BillService   + BillController
    ├── AiService     + AiController
    └── FileService   + FileController
src/main/resources/mapper/api/V2Mapper.xml
```

### 7.2 设计约束

- `@RestControllerAdvice(basePackages="com.wt.hrentSystem.api.v2")` 限定异常处理作用域，不影响存量 14 个 Controller。
- `V2Context` 严格鉴权（无 token → 600）；**不复用**存量 `UserUtils` 的「回退管理员」兜底。
- 所有 SQL 参数化（`#{}`），`LIKE` 用 `CONCAT('%',#{x},'%')`，排序字段白名单固化在 XML 中。
- 金额一律 `long fen`；写接口用乐观锁 `version` 或 `Idempotency-Key` + 唯一键保证幂等。
- `application.yml` 本次仅新增 `spring.servlet.multipart.max-file-size=5MB` / `max-request-size=6MB`，其余存量配置未动。
- 数据库凭据、微信 appSecret、JWT secret 一律走配置文件/环境变量，**不得提交明文到代码库**；生产环境请替换 `application.yml` 中的默认值。

### 7.3 编译与启动

> 已内置 Maven Wrapper（Maven 3.9.16），**无需预装 Maven**，也不依赖任何人机器上的 `mvn` 路径。

**推荐：在项目根目录用一键脚本**（自动判断是否需构建、按 JDK 版本拼 `--add-opens`、后台启停 + 健康检查 + 日志轮转）：

```bash
cp .env.example .env      # 首次：填数据库口令与 JWT 密钥（.env 不入库）
./start.sh                # Linux / macOS / WSL / Git Bash
start.cmd                 # Windows
```

完整命令、环境变量与服务器部署见 `docs/24_一键启动与部署指南.md`。以下是手动执行的等价步骤。

```bash
# 标准入口：用 Wrapper（推荐，Linux/macOS）
./mvnw clean package                    # 首次会下载 Maven 发行版与依赖
# Windows
mvnw.cmd clean package

# 本机 ~/.m2/settings.xml 把 localRepository 写死成别人的绝对路径时，改用项目模板
./mvnw -s .mvn/settings.xml clean package

# 启动（JDK17 需 add-opens；端口取 application.yml，也可显式覆盖）
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.math=ALL-UNNAMED \
     -jar target/hrent-0.0.1-SNAPSHOT.jar --server.port=8888
# 预期：Started HrentApplication；Flyway: Successfully validated 8 migrations
# 服务地址 http://localhost:8888/hrent （port 8888 + context-path /hrent）
```

**2026-11-11 实测**：`./mvnw -s .mvn/settings.xml clean package` → `BUILD SUCCESS`，`Tests run: 3, Failures: 0, Errors: 0`，产出 29 MB fat jar；启动 `Started HrentApplication in 21.155s`。一键脚本 `./start.sh build` 同样构建成功（1m46s）。

`pom.xml` 已含 `javax.xml.bind:jaxb-api:2.3.1`（`jjwt 0.9.1` 在 JDK11+ 缺 `DatatypeConverter` 的兜底，见 `docs/23` T-07）。

以下静态校验命令保留作**离线兜底**（无网、无法下载依赖时，仅覆盖语法与类型引用，不做运行时启动）：

```bash
javac -encoding UTF-8 -cp "<本地 ~/.m2/repository 下全部 jar>" -d /tmp/v2out $(find src/main/java -name '*.java')
python -c "import xml.etree.ElementTree as E,glob; [E.parse(f) for f in glob.glob('src/main/resources/mapper/**/*.xml', recursive=True)]"
```

`.mvn/settings.xml` 是**可选模板**（无绝对路径/凭据/镜像）；不通过 `maven.config` 强制引用，避免覆盖同事本机的公司镜像配置。`.mvn/jvm.config` 未生成，以兼容 JDK8。

### 7.4 已知边界

- `jjwt 0.9.1` 在 JDK17 缺 `javax.xml.bind`，W1 依赖 spike 未闭环；运行时需 JDK8 或 `--add-opens` + `jaxb-api` 兜底（待定 T-07）。
- AI 三级降级、图片压缩与缩略图未接入，详见 `docs/19_待定事项清单.md`。



