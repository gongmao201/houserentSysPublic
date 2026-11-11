-- =====================================================================
-- V1 · 存量基线（13 张表）
-- 来源：hrent-api/sql/house_rent.sql（逆向重建版），逐列复制，不改字段
-- 目的：让 Flyway 在"新库"和"已存在的老库"上都能跑
--   · 新库：本脚本创建 13 张表
--   · 老库：CREATE TABLE IF NOT EXISTS 全部命中，等价于空操作
-- 禁止：DROP DATABASE / DROP TABLE（原脚本第 12 行有 DROP，已删除）
-- 字符集：统一 utf8mb4 / utf8mb4_general_ci（与存量库一致，避免 JOIN 排序规则冲突）
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 系统用户表  role: 0=租户 1=房东 2=超管
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          VARCHAR(32)  NOT NULL COMMENT '主键',
    role        INT          NOT NULL DEFAULT 0 COMMENT '角色 0租户 1房东 2超管',
    role_str    VARCHAR(32)  DEFAULT NULL COMMENT '角色名称',
    name        VARCHAR(64)  DEFAULT NULL COMMENT '用户名',
    login_name  VARCHAR(64)  DEFAULT NULL COMMENT '登录名',
    password    VARCHAR(64)  DEFAULT NULL COMMENT '密码(MD5)',
    email       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    mobile      VARCHAR(16)  DEFAULT NULL COMMENT '手机号',
    p_id        VARCHAR(32)  DEFAULT NULL COMMENT '头像图片id',
    sex         VARCHAR(2)   DEFAULT NULL COMMENT '性别 0男 1女',
    birthday    DATETIME     DEFAULT NULL COMMENT '出生日期',
    openid      VARCHAR(64)  DEFAULT NULL COMMENT '微信openid',
    login_time  DATETIME     DEFAULT NULL COMMENT '最近登录时间',
    create_by   VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by   VARCHAR(32)  DEFAULT NULL COMMENT '修改人',
    update_time DATETIME     DEFAULT NULL COMMENT '修改时间',
    remake      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    del_flag    INT          NOT NULL DEFAULT 0 COMMENT '删除标志 0正常 1删除',
    PRIMARY KEY (id),
    KEY idx_login_name (login_name),
    KEY idx_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统用户表';

-- ---------------------------------------------------------------------
-- 2. 房源信息表  state: 0审核中 1已审核 2已租
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_house_resource (
    id          VARCHAR(32)  NOT NULL COMMENT '主键(HR+时间戳)',
    province    VARCHAR(64)  DEFAULT NULL COMMENT '省',
    city        VARCHAR(64)  DEFAULT NULL COMMENT '市',
    county      VARCHAR(64)  DEFAULT NULL COMMENT '区县',
    address     VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
    money       DOUBLE       DEFAULT NULL COMMENT '租金(元/月)',
    area        INT          DEFAULT NULL COMMENT '面积(平方米)',
    `describe`  VARCHAR(1000) DEFAULT NULL COMMENT '房源描述',
    house_type  VARCHAR(32)  DEFAULT NULL COMMENT '户型(字典house_type)',
    floor       INT          DEFAULT NULL COMMENT '楼层',
    build_time  VARCHAR(32)  DEFAULT NULL COMMENT '修建时间',
    rent_type   INT          DEFAULT NULL COMMENT '出租类型 0整租 1合租',
    people_num  INT          DEFAULT NULL COMMENT '合租人数(整租为1)',
    latitude    DECIMAL(10, 6) DEFAULT NULL COMMENT '纬度',
    longitude   DECIMAL(10, 6) DEFAULT NULL COMMENT '经度',
    state       INT          NOT NULL DEFAULT 0 COMMENT '状态 0审核中 1已审核 2已租',
    create_by   VARCHAR(32)  DEFAULT NULL COMMENT '创建人(房东)',
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    remake      VARCHAR(500) DEFAULT NULL,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_state (state),
    KEY idx_create_by (create_by)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '房源信息表';

-- ---------------------------------------------------------------------
-- 3. 图片表  type: 1轮播 2按钮 3公用 4系统 5房源 6用户
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_pic (
    id          VARCHAR(32)  NOT NULL,
    name        VARCHAR(128) DEFAULT NULL COMMENT '图片名称',
    type        INT          DEFAULT NULL COMMENT '图片类型',
    state       INT          DEFAULT NULL COMMENT '状态',
    url         VARCHAR(500) DEFAULT NULL COMMENT '访问路径',
    house_id    VARCHAR(32)  DEFAULT NULL COMMENT '关联房源id',
    create_by   VARCHAR(32)  DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    remake      VARCHAR(500) DEFAULT NULL,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_house_id (house_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '图片表';

-- ---------------------------------------------------------------------
-- 4. 标签表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_tag (
    id          VARCHAR(32)  NOT NULL,
    name        VARCHAR(64)  DEFAULT NULL COMMENT '标签名',
    rgb         VARCHAR(32)  DEFAULT NULL COMMENT '标签颜色',
    state       VARCHAR(2)   DEFAULT NULL COMMENT '状态 0待审核 1通过 2未通过',
    create_by   VARCHAR(32)  DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    remake      VARCHAR(500) DEFAULT NULL,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签表';

-- ---------------------------------------------------------------------
-- 5. 标签-房源关系表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_tag_and_house (
    id          VARCHAR(32)  NOT NULL,
    house_id    VARCHAR(32)  DEFAULT NULL COMMENT '房源id',
    tag_id      VARCHAR(32)  DEFAULT NULL COMMENT '标签id',
    create_by   VARCHAR(32)  DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    remake      VARCHAR(500) DEFAULT NULL,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_house_id (house_id),
    KEY idx_tag_id (tag_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签房源关系表';

-- ---------------------------------------------------------------------
-- 6. 看房申请表  state: 0申请中 1通过 2另选时间 3拒绝 4另选拒绝 5已结束 6另选通过
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_apply_view (
    id            VARCHAR(32)  NOT NULL,
    apply_user_id VARCHAR(32)  DEFAULT NULL COMMENT '申请人id',
    house_id      VARCHAR(32)  DEFAULT NULL COMMENT '房源id',
    view_time     VARCHAR(50)  DEFAULT NULL COMMENT '预约看房时间',
    view_time_re  VARCHAR(50)  DEFAULT NULL COMMENT '重新选择的时间',
    state         INT          NOT NULL DEFAULT 0 COMMENT '状态',
    create_by     VARCHAR(32)  DEFAULT NULL,
    create_time   DATETIME     DEFAULT NULL,
    update_by     VARCHAR(32)  DEFAULT NULL,
    update_time   DATETIME     DEFAULT NULL,
    remake        VARCHAR(500) DEFAULT NULL,
    del_flag      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_house_id (house_id),
    KEY idx_apply_user (apply_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '看房申请表';

-- ---------------------------------------------------------------------
-- 7. 租房申请表  state: 0申请中 1通过 2未通过 3押金已付 4押金未付 5已完成
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_apply_rent (
    id            VARCHAR(32)  NOT NULL,
    apply_user_id VARCHAR(32)  DEFAULT NULL COMMENT '申请人id',
    house_id      VARCHAR(32)  DEFAULT NULL COMMENT '房源id',
    start_time    DATETIME     DEFAULT NULL COMMENT '租房开始时间',
    end_time      DATETIME     DEFAULT NULL COMMENT '租房结束时间',
    state         INT          NOT NULL DEFAULT 0 COMMENT '状态',
    deposit       DOUBLE       DEFAULT NULL COMMENT '押金',
    contract_url  VARCHAR(500) DEFAULT NULL COMMENT '合同地址',
    create_by     VARCHAR(32)  DEFAULT NULL,
    create_time   DATETIME     DEFAULT NULL,
    update_by     VARCHAR(32)  DEFAULT NULL,
    update_time   DATETIME     DEFAULT NULL,
    remake        VARCHAR(500) DEFAULT NULL,
    del_flag      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_house_id (house_id),
    KEY idx_apply_user (apply_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租房申请表';

-- ---------------------------------------------------------------------
-- 8. 在租信息表（原 start_time/end_time 为 VARCHAR，V2 新增 DATE 字段）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_house_renting (
    id          VARCHAR(32)  NOT NULL,
    house_id    VARCHAR(32)  DEFAULT NULL COMMENT '房源id',
    contract    VARCHAR(500) DEFAULT NULL COMMENT '合同地址',
    tenant_id   VARCHAR(32)  DEFAULT NULL COMMENT '租客id',
    money       DOUBLE       DEFAULT NULL COMMENT '租金',
    start_time  VARCHAR(50)  DEFAULT NULL COMMENT '开始时间',
    end_time    VARCHAR(50)  DEFAULT NULL COMMENT '结束时间',
    create_by   VARCHAR(32)  DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    remake      VARCHAR(500) DEFAULT NULL,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_house_id (house_id),
    KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '在租信息表';

-- ---------------------------------------------------------------------
-- 9. 租金记录表（V5 扩展为账单主表）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_rent (
    id           VARCHAR(32)  NOT NULL,
    renting_id   VARCHAR(32)  DEFAULT NULL COMMENT '在租信息id',
    payable_time DATETIME     DEFAULT NULL COMMENT '应缴日期',
    pay_time     DATETIME     DEFAULT NULL COMMENT '实缴日期',
    tenant_id    VARCHAR(32)  DEFAULT NULL COMMENT '租客id',
    state        INT          NOT NULL DEFAULT 0 COMMENT '状态',
    create_by    VARCHAR(32)  DEFAULT NULL,
    create_time  DATETIME     DEFAULT NULL,
    update_by    VARCHAR(32)  DEFAULT NULL,
    update_time  DATETIME     DEFAULT NULL,
    remake       VARCHAR(500) DEFAULT NULL,
    del_flag     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_renting (renting_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '租金记录表(账单主表)';

-- ---------------------------------------------------------------------
-- 10. 收藏表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_collection (
    id          VARCHAR(32)  NOT NULL,
    house_id    VARCHAR(32)  DEFAULT NULL COMMENT '房源id',
    user_id     VARCHAR(32)  DEFAULT NULL COMMENT '收藏人id',
    create_by   VARCHAR(32)  DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    remake      VARCHAR(500) DEFAULT NULL,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user (user_id),
    KEY idx_house (house_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '收藏表';

-- ---------------------------------------------------------------------
-- 11. 站内消息表  receiver_type: 0个人 1系统
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_message (
    id            VARCHAR(32)  NOT NULL,
    receiver_type INT          DEFAULT NULL COMMENT '接收者类型',
    receiver_id   VARCHAR(32)  DEFAULT NULL COMMENT '接收者id',
    message_name  VARCHAR(128) DEFAULT NULL COMMENT '消息名称',
    text          VARCHAR(1000) DEFAULT NULL COMMENT '消息内容',
    title         VARCHAR(255) DEFAULT NULL COMMENT '标题',
    state         INT          NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `system`      VARCHAR(2)   DEFAULT NULL COMMENT '系统标识',
    create_by     VARCHAR(32)  DEFAULT NULL,
    create_time   DATETIME     DEFAULT NULL,
    update_by     VARCHAR(32)  DEFAULT NULL,
    update_time   DATETIME     DEFAULT NULL,
    remake        VARCHAR(500) DEFAULT NULL,
    del_flag      INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_receiver (receiver_id, state)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '站内消息表';

-- ---------------------------------------------------------------------
-- 12. 菜单表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
    id          VARCHAR(32)  NOT NULL,
    parent_id   VARCHAR(32)  DEFAULT NULL COMMENT '父菜单id',
    role_id     VARCHAR(32)  DEFAULT NULL COMMENT '角色id 0租户 1房东 2超管',
    sort        INT          DEFAULT 30 COMMENT '排序',
    name        VARCHAR(64)  DEFAULT NULL COMMENT '菜单名',
    url         VARCHAR(128) DEFAULT NULL COMMENT '路由地址',
    `system`    VARCHAR(2)   DEFAULT '1' COMMENT '系统标识',
    icon        VARCHAR(64)  DEFAULT NULL COMMENT '图标class',
    create_by   VARCHAR(32)  DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    remake      VARCHAR(500) DEFAULT NULL,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '菜单表';

-- ---------------------------------------------------------------------
-- 13. 数据字典表  type=hrent_role_type / house_type
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_dict (
    id          VARCHAR(32)  NOT NULL,
    `value`     VARCHAR(64)  DEFAULT NULL COMMENT '字典值',
    label       VARCHAR(128) DEFAULT NULL COMMENT '展示名',
    type        VARCHAR(64)  DEFAULT NULL COMMENT '字典类型',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述',
    sort        VARCHAR(8)   DEFAULT NULL COMMENT '排序',
    create_by   VARCHAR(32)  DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    remake      VARCHAR(500) DEFAULT NULL,
    del_flag    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '数据字典表';

-- ---------------------------------------------------------------------
-- 基础字典与管理员（新库必备；老库用 INSERT IGNORE 保证不重复）
-- 演示账号口令哈希为占位值，部署前请按 PUBLISH_NOTES.md 自行设置
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_user (id, role, role_str, name, login_name, password, email, mobile, sex, create_by, create_time, update_by, update_time, del_flag)
VALUES ('1', 2, '超级管理员', '系统管理员', 'admin', '00000000000000000000000000000000', 'demo@example.invalid', '138****0000', '0', '1', NOW(), '1', NOW(), 0);

INSERT IGNORE INTO hrent_pic (id, name, type, state, url, create_by, create_time, update_by, update_time, del_flag)
VALUES ('1', '系统logo', 4, 1, '/default/system/loginLogo.png', '1', NOW(), '1', NOW(), 0);

INSERT IGNORE INTO sys_dict (id, `value`, label, type, description, sort, create_by, create_time, update_by, update_time, del_flag)
VALUES ('0', '0', '租户', 'hrent_role_type', '用户角色', '1', '1', NOW(), '1', NOW(), 0),
       ('1', '1', '房东', 'hrent_role_type', '用户角色', '2', '1', NOW(), '1', NOW(), 0),
       ('2', '2', '超级管理员', 'hrent_role_type', '用户角色', '3', '1', NOW(), '1', NOW(), 0),
       ('11', '1', '一室一厅', 'house_type', '户型', '1', '1', NOW(), '1', NOW(), 0),
       ('12', '2', '两室一厅', 'house_type', '户型', '2', '1', NOW(), '1', NOW(), 0),
       ('13', '3', '三室一厅', 'house_type', '户型', '3', '1', NOW(), '1', NOW(), 0),
       ('14', '4', '四室一厅', 'house_type', '户型', '4', '1', NOW(), '1', NOW(), 0),
       ('15', '5', '单间', 'house_type', '户型', '5', '1', NOW(), '1', NOW(), 0),
       ('16', '6', '其他', 'house_type', '户型', '6', '1', NOW(), '1', NOW(), 0);

-- ---------------------------------------------------------------------
-- 基础菜单（空库必备；老库用 INSERT IGNORE 保证不重复）
-- 来源：sql/house_rent.sql:347-380，共 4 条 INSERT、24 行
-- 没有这段，空库走 V1–V6 后登录，左侧菜单是空的
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, role_id, sort, name, url, icon, `system`, create_by, create_time, update_by, update_time, del_flag)
VALUES ('100', NULL, '0', 1, '数据展示', '/showData', 'el-icon-monitor', '1', '1', NOW(), '1', NOW(), 0),
       ('101', NULL, '1', 1, '数据展示', '/showData', 'el-icon-monitor', '1', '1', NOW(), '1', NOW(), 0),
       ('102', NULL, '2', 1, '数据展示', '/showData', 'el-icon-monitor', '1', '1', NOW(), '1', NOW(), 0);

INSERT IGNORE INTO sys_menu (id, parent_id, role_id, sort, name, url, icon, `system`, create_by, create_time, update_by, update_time, del_flag)
VALUES ('110', NULL, '1', 2, '房源管理', '/house', 'el-icon-house', '1', '1', NOW(), '1', NOW(), 0),
       ('111', '110', '1', 1, '房源列表', '/houseManage', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('112', '110', '1', 2, '在租列表', '/houseRenting', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('113', '110', '1', 3, '租金记录', '/rentRecord', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('120', NULL, '2', 2, '房源管理', '/house', 'el-icon-house', '1', '1', NOW(), '1', NOW(), 0),
       ('121', '120', '2', 1, '房源列表', '/houseManage', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('122', '120', '2', 2, '在租列表', '/houseRenting', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('123', '120', '2', 3, '租金记录', '/rentRecord', NULL, '1', '1', NOW(), '1', NOW(), 0);

INSERT IGNORE INTO sys_menu (id, parent_id, role_id, sort, name, url, icon, `system`, create_by, create_time, update_by, update_time, del_flag)
VALUES ('130', NULL, '0', 3, '申请管理', '/apply', 'el-icon-message', '1', '1', NOW(), '1', NOW(), 0),
       ('131', '130', '0', 1, '看房申请', '/lookHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('132', '130', '0', 2, '租房申请', '/rentHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('140', NULL, '1', 3, '申请管理', '/apply', 'el-icon-message', '1', '1', NOW(), '1', NOW(), 0),
       ('141', '140', '1', 1, '看房申请', '/lookHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('142', '140', '1', 2, '租房申请', '/rentHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('150', NULL, '2', 3, '申请管理', '/apply', 'el-icon-message', '1', '1', NOW(), '1', NOW(), 0),
       ('151', '150', '2', 1, '看房申请', '/lookHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('152', '150', '2', 2, '租房申请', '/rentHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0);

INSERT IGNORE INTO sys_menu (id, parent_id, role_id, sort, name, url, icon, `system`, create_by, create_time, update_by, update_time, del_flag)
VALUES ('160', NULL, '2', 4, '图片管理', '/picManage', 'el-icon-picture-outline', '1', '1', NOW(), '1', NOW(), 0),
       ('170', NULL, '2', 5, '用户管理', '/userManage', 'el-icon-user', '1', '1', NOW(), '1', NOW(), 0),
       ('180', NULL, '2', 6, '字典管理', '/dictManage', 'el-icon-notebook-1', '1', '1', NOW(), '1', NOW(), 0),
       ('190', NULL, '2', 7, '菜单管理', '/menuManage', 'el-icon-s-order', '1', '1', NOW(), '1', NOW(), 0);
