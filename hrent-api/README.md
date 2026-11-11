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

```bash
# 联网机器（推荐）
mvn clean package -DskipTests
mvn spring-boot:run
# 预期：Started HrentApplication；Flyway: Successfully validated 8 migrations
# 服务地址 http://localhost:8888/hrent （port 8888 + context-path /hrent）
```

本环境 `mvn` 不在 PATH 且无法访问 Maven 仓库，改用如下等价校验（覆盖语法与类型引用，不做运行时启动）：

```bash
javac -encoding UTF-8 -cp "<本地 ~/.m2/repository 下全部 jar>" -d /tmp/v2out $(find src/main/java -name '*.java')
python -c "import xml.etree.ElementTree as E,glob; [E.parse(f) for f in glob.glob('src/main/resources/mapper/**/*.xml', recursive=True)]"
```

### 7.4 已知边界

- `jjwt 0.9.1` 在 JDK17 缺 `javax.xml.bind`，W1 依赖 spike 未闭环；运行时需 JDK8 或 `--add-opens` + `jaxb-api` 兜底（待定 T-07）。
- AI 三级降级、图片压缩与缩略图未接入，详见 `docs/19_待定事项清单.md`。



