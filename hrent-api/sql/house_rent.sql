-- =====================================================================
-- 房屋租赁管理系统（hrent）数据库初始化脚本
-- 适用 MySQL 5.7+ / 8.0
-- 说明：原始仓库未提供 SQL 脚本（README 要求"执行项目的 sql"但文件缺失），
--       本脚本依据全部 Mapper XML 与实体类逆向重建，列名/列类型与
--       resultMap 的 jdbcType 声明一一对应。
-- 使用方法（Windows PowerShell 务必加 --default-character-set=utf8mb4，否则中文注释乱码）：
--   cmd /c "mysql -uroot -p --default-character-set=utf8mb4 < house_rent.sql"
-- =====================================================================

-- 重复执行安全：先清掉旧库
DROP DATABASE IF EXISTS house_rent;

CREATE DATABASE IF NOT EXISTS house_rent
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE house_rent;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. 系统用户表（租户/房东/超级管理员）
--    role: 0=租户 1=房东 2=超级管理员
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
) ENGINE = InnoDB COMMENT = '系统用户表';

-- ---------------------------------------------------------------------
-- 2. 房源信息表
--    state: 0=审核中 1=已审核(在租列表可见) 2=已租
--    rent_type: 0=整租 1=合租
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_house_resource (
    id         VARCHAR(32)  NOT NULL COMMENT '主键(HR+时间戳)',
    province   VARCHAR(64)  DEFAULT NULL COMMENT '省',
    city       VARCHAR(64)  DEFAULT NULL COMMENT '市',
    county     VARCHAR(64)  DEFAULT NULL COMMENT '区县',
    address    VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
    money      DOUBLE       DEFAULT NULL COMMENT '租金(元/月)',
    area       INT          DEFAULT NULL COMMENT '面积(平方米)',
    `describe` VARCHAR(1000) DEFAULT NULL COMMENT '房源描述',
    house_type VARCHAR(32)  DEFAULT NULL COMMENT '户型(字典house_type)',
    floor      INT          DEFAULT NULL COMMENT '楼层',
    build_time VARCHAR(32)  DEFAULT NULL COMMENT '修建时间',
    rent_type  INT          DEFAULT NULL COMMENT '出租类型 0整租 1合租',
    people_num INT          DEFAULT NULL COMMENT '合租人数(整租为1)',
    latitude   DECIMAL(10, 6) DEFAULT NULL COMMENT '纬度',
    longitude  DECIMAL(10, 6) DEFAULT NULL COMMENT '经度',
    state      INT          NOT NULL DEFAULT 0 COMMENT '状态 0审核中 1已审核 2已租',
    create_by  VARCHAR(32)  DEFAULT NULL COMMENT '创建人(房东)',
    create_time DATETIME    DEFAULT NULL,
    update_by  VARCHAR(32)  DEFAULT NULL,
    update_time DATETIME    DEFAULT NULL,
    remake     VARCHAR(500) DEFAULT NULL,
    del_flag   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_state (state),
    KEY idx_create_by (create_by)
) ENGINE = InnoDB COMMENT = '房源信息表';

-- ---------------------------------------------------------------------
-- 3. 图片表
--    type: 1轮播图 2按钮 3公用 4系统 5房源 6用户
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
) ENGINE = InnoDB COMMENT = '图片表';

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
) ENGINE = InnoDB COMMENT = '标签表';

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
) ENGINE = InnoDB COMMENT = '标签房源关系表';

-- ---------------------------------------------------------------------
-- 6. 看房申请表
--    state: 0申请中 1通过 2另选时间 3拒绝 4另选时间拒绝 5已结束 6另选时间通过
--    view_time 为 VARCHAR（与原 resultMap jdbcType=VARCHAR 保持一致）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_apply_view (
    id           VARCHAR(32)  NOT NULL,
    apply_user_id VARCHAR(32) DEFAULT NULL COMMENT '申请人id',
    house_id     VARCHAR(32)  DEFAULT NULL COMMENT '房源id',
    view_time    VARCHAR(50)  DEFAULT NULL COMMENT '预约看房时间',
    view_time_re VARCHAR(50)  DEFAULT NULL COMMENT '重新选择的时间',
    state        INT          NOT NULL DEFAULT 0 COMMENT '状态',
    create_by    VARCHAR(32)  DEFAULT NULL,
    create_time  DATETIME     DEFAULT NULL,
    update_by    VARCHAR(32)  DEFAULT NULL,
    update_time  DATETIME     DEFAULT NULL,
    remake       VARCHAR(500) DEFAULT NULL,
    del_flag     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_house_id (house_id),
    KEY idx_apply_user (apply_user_id)
) ENGINE = InnoDB COMMENT = '看房申请表';

-- ---------------------------------------------------------------------
-- 7. 租房申请表
--    state: 0申请中 1通过 2未通过 3押金已支付 4押金未支付 5已完成
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hrent_apply_rent (
    id           VARCHAR(32)  NOT NULL,
    apply_user_id VARCHAR(32) DEFAULT NULL COMMENT '申请人id',
    house_id     VARCHAR(32)  DEFAULT NULL COMMENT '房源id',
    start_time   DATETIME     DEFAULT NULL COMMENT '租房开始时间',
    end_time     DATETIME     DEFAULT NULL COMMENT '租房结束时间',
    state        INT          NOT NULL DEFAULT 0 COMMENT '状态',
    deposit      DOUBLE       DEFAULT NULL COMMENT '押金',
    contract_url VARCHAR(500) DEFAULT NULL COMMENT '合同地址',
    create_by    VARCHAR(32)  DEFAULT NULL,
    create_time  DATETIME     DEFAULT NULL,
    update_by    VARCHAR(32)  DEFAULT NULL,
    update_time  DATETIME     DEFAULT NULL,
    remake       VARCHAR(500) DEFAULT NULL,
    del_flag     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_house_id (house_id),
    KEY idx_apply_user (apply_user_id)
) ENGINE = InnoDB COMMENT = '租房申请表';

-- ---------------------------------------------------------------------
-- 8. 在租信息表（start_time/end_time 为 VARCHAR，与原 resultMap 一致）
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
) ENGINE = InnoDB COMMENT = '在租信息表';

-- ---------------------------------------------------------------------
-- 9. 租金记录表
--    state: 0未到时间 1已到时间 2已交租
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
) ENGINE = InnoDB COMMENT = '租金记录表';

-- ---------------------------------------------------------------------
-- 10. 收藏表（物理删除）
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
) ENGINE = InnoDB COMMENT = '收藏表';

-- ---------------------------------------------------------------------
-- 11. 站内消息表
--     receiver_type: 0=个人 1=系统
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_message (
    id           VARCHAR(32)  NOT NULL,
    receiver_type INT         DEFAULT NULL COMMENT '接收者类型',
    receiver_id  VARCHAR(32)  DEFAULT NULL COMMENT '接收者id',
    message_name VARCHAR(128) DEFAULT NULL COMMENT '消息名称',
    text         VARCHAR(1000) DEFAULT NULL COMMENT '消息内容',
    title        VARCHAR(255) DEFAULT NULL COMMENT '标题',
    state        INT          NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    `system`     VARCHAR(2)   DEFAULT NULL COMMENT '系统标识',
    create_by    VARCHAR(32)  DEFAULT NULL,
    create_time  DATETIME     DEFAULT NULL,
    update_by    VARCHAR(32)  DEFAULT NULL,
    update_time  DATETIME     DEFAULT NULL,
    remake       VARCHAR(500) DEFAULT NULL,
    del_flag     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_receiver (receiver_id, state)
) ENGINE = InnoDB COMMENT = '站内消息表';

-- ---------------------------------------------------------------------
-- 12. 菜单表（按角色动态菜单）
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
) ENGINE = InnoDB COMMENT = '菜单表';

-- ---------------------------------------------------------------------
-- 13. 数据字典表
--     type=hrent_role_type: 角色字典（id 与 value 一致，前端按 id 取值）
--     type=house_type: 户型字典（房源表 house_type 关联 value）
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
) ENGINE = InnoDB COMMENT = '数据字典表';

-- =====================================================================
-- 种子数据
-- 演示账号口令哈希为占位值，部署前请按 PUBLISH_NOTES.md 自行设置
-- =====================================================================

-- 用户：<DEMO_ACCOUNT>/<DEMO_PASSWORD>（超管）、lisi/<DEMO_PASSWORD>（房东）、zhangsan/<DEMO_PASSWORD>（租户）
INSERT INTO sys_user (id, role, role_str, name, login_name, password, email, mobile, sex, create_by, create_time, update_by, update_time, del_flag)
VALUES ('1', 2, '超级管理员', '系统管理员', 'admin', '00000000000000000000000000000000', 'demo@example.invalid', '138****0000', '0', '1', NOW(), '1', NOW(), 0),
       ('2', 1, '房东', '演示用户B', 'lisi', '00000000000000000000000000000000', 'demo@example.invalid', '138****0000', '0', '1', NOW(), '1', NOW(), 0),
       ('3', 0, '租户', '演示用户A', 'zhangsan', '00000000000000000000000000000000', 'demo@example.invalid', '138****0000', '1', '1', NOW(), '1', NOW(), 0);

-- 系统 logo 图片（SysCommonController.getSystemResource 读取 id=1）
INSERT INTO hrent_pic (id, name, type, state, url, create_by, create_time, update_by, update_time, del_flag)
VALUES ('1', '系统logo', 4, 1, '/default/system/loginLogo.png', '1', NOW(), '1', NOW(), 0);

-- 字典：角色
INSERT INTO sys_dict (id, `value`, label, type, description, sort, create_by, create_time, update_by, update_time, del_flag)
VALUES ('0', '0', '租户', 'hrent_role_type', '用户角色', '1', '1', NOW(), '1', NOW(), 0),
       ('1', '1', '房东', 'hrent_role_type', '用户角色', '2', '1', NOW(), '1', NOW(), 0),
       ('2', '2', '超级管理员', 'hrent_role_type', '用户角色', '3', '1', NOW(), '1', NOW(), 0);

-- 字典：户型
INSERT INTO sys_dict (id, `value`, label, type, description, sort, create_by, create_time, update_by, update_time, del_flag)
VALUES ('11', '1', '一室一厅', 'house_type', '户型', '1', '1', NOW(), '1', NOW(), 0),
       ('12', '2', '两室一厅', 'house_type', '户型', '2', '1', NOW(), '1', NOW(), 0),
       ('13', '3', '三室一厅', 'house_type', '户型', '3', '1', NOW(), '1', NOW(), 0),
       ('14', '4', '四室一厅', 'house_type', '户型', '4', '1', NOW(), '1', NOW(), 0),
       ('15', '5', '单间', 'house_type', '户型', '5', '1', NOW(), '1', NOW(), 0),
       ('16', '6', '其他', 'house_type', '户型', '6', '1', NOW(), '1', NOW(), 0);

-- 菜单（system=1 为 Web 管理端菜单；前端按登录角色过滤）
-- 数据展示（全部角色）
INSERT INTO sys_menu (id, parent_id, role_id, sort, name, url, icon, `system`, create_by, create_time, update_by, update_time, del_flag)
VALUES ('100', NULL, '0', 1, '数据展示', '/showData', 'el-icon-monitor', '1', '1', NOW(), '1', NOW(), 0),
       ('101', NULL, '1', 1, '数据展示', '/showData', 'el-icon-monitor', '1', '1', NOW(), '1', NOW(), 0),
       ('102', NULL, '2', 1, '数据展示', '/showData', 'el-icon-monitor', '1', '1', NOW(), '1', NOW(), 0);

-- 房源管理（房东/超管）
INSERT INTO sys_menu (id, parent_id, role_id, sort, name, url, icon, `system`, create_by, create_time, update_by, update_time, del_flag)
VALUES ('110', NULL, '1', 2, '房源管理', '/house', 'el-icon-house', '1', '1', NOW(), '1', NOW(), 0),
       ('111', '110', '1', 1, '房源列表', '/houseManage', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('112', '110', '1', 2, '在租列表', '/houseRenting', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('113', '110', '1', 3, '租金记录', '/rentRecord', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('120', NULL, '2', 2, '房源管理', '/house', 'el-icon-house', '1', '1', NOW(), '1', NOW(), 0),
       ('121', '120', '2', 1, '房源列表', '/houseManage', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('122', '120', '2', 2, '在租列表', '/houseRenting', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('123', '120', '2', 3, '租金记录', '/rentRecord', NULL, '1', '1', NOW(), '1', NOW(), 0);

-- 申请管理（全部角色）
INSERT INTO sys_menu (id, parent_id, role_id, sort, name, url, icon, `system`, create_by, create_time, update_by, update_time, del_flag)
VALUES ('130', NULL, '0', 3, '申请管理', '/apply', 'el-icon-message', '1', '1', NOW(), '1', NOW(), 0),
       ('131', '130', '0', 1, '看房申请', '/lookHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('132', '130', '0', 2, '租房申请', '/rentHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('140', NULL, '1', 3, '申请管理', '/apply', 'el-icon-message', '1', '1', NOW(), '1', NOW(), 0),
       ('141', '140', '1', 1, '看房申请', '/lookHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('142', '140', '1', 2, '租房申请', '/rentHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('150', NULL, '2', 3, '申请管理', '/apply', 'el-icon-message', '1', '1', NOW(), '1', NOW(), 0),
       ('151', '150', '2', 1, '看房申请', '/lookHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0),
       ('152', '150', '2', 2, '租房申请', '/rentHouseApply', NULL, '1', '1', NOW(), '1', NOW(), 0);

-- 图片/用户/字典/菜单管理（超管）
INSERT INTO sys_menu (id, parent_id, role_id, sort, name, url, icon, `system`, create_by, create_time, update_by, update_time, del_flag)
VALUES ('160', NULL, '2', 4, '图片管理', '/picManage', 'el-icon-picture-outline', '1', '1', NOW(), '1', NOW(), 0),
       ('170', NULL, '2', 5, '用户管理', '/userManage', 'el-icon-user', '1', '1', NOW(), '1', NOW(), 0),
       ('180', NULL, '2', 6, '字典管理', '/dictManage', 'el-icon-notebook-1', '1', '1', NOW(), '1', NOW(), 0),
       ('190', NULL, '2', 7, '菜单管理', '/menuManage', 'el-icon-s-order', '1', '1', NOW(), '1', NOW(), 0);

-- 标签
INSERT INTO hrent_tag (id, name, rgb, state, create_by, create_time, update_by, update_time, del_flag)
VALUES ('9001', '近地铁', '64,158,255', '1', '1', NOW(), '1', NOW(), 0),
       ('9002', '精装修', '255,153,0', '1', '1', NOW(), '1', NOW(), 0),
       ('9003', '南北通透', '103,194,58', '1', '1', NOW(), '1', NOW(), 0),
       ('9004', '家电齐全', '144,147,153', '1', '1', NOW(), '1', NOW(), 0);

-- 演示房源（房东 lisi 发布，已审核）
INSERT INTO hrent_house_resource (id, province, city, county, address, money, area, `describe`, house_type, floor, build_time, rent_type, people_num, latitude, longitude, state, create_by, create_time, update_by, update_time, remake, del_flag)
VALUES ('HR20260101000001', '四川省', '成都市', '武侯区', '科华北路100号', 1500, 50, '精装修拎包入住，家电齐全，地铁3号线附近', '2', 5, '2015', 1, 3, 30.630000, 104.070000, 1, '2', NOW(), '2', NOW(), NULL, 0),
       ('HR20260101000002', '四川省', '成都市', '高新区', '天府三街 200 号', 2800, 89, '高层景观房，采光好，配套成熟', '3', 18, '2018', 0, 1, 30.550000, 104.060000, 1, '2', NOW(), '2', NOW(), NULL, 0);

-- 房源图片（占位图位于 hrent-api/src/main/resources/static/images/default/house/）
INSERT INTO hrent_pic (id, name, type, state, url, house_id, create_by, create_time, update_by, update_time, del_flag)
VALUES ('9101', '房源图1', 5, 1, '/default/house/house1.png', 'HR20260101000001', '2', NOW(), '2', NOW(), 0),
       ('9102', '房源图2', 5, 1, '/default/house/house2.png', 'HR20260101000001', '2', NOW(), '2', NOW(), 0),
       ('9103', '房源图1', 5, 1, '/default/house/house1.png', 'HR20260101000002', '2', NOW(), '2', NOW(), 0);

-- 房源标签关系
INSERT INTO hrent_tag_and_house (id, house_id, tag_id, create_by, create_time, update_by, update_time, del_flag)
VALUES ('9201', 'HR20260101000001', '9001', '2', NOW(), '2', NOW(), 0),
       ('9202', 'HR20260101000001', '9002', '2', NOW(), '2', NOW(), 0),
       ('9203', 'HR20260101000001', '9004', '2', NOW(), '2', NOW(), 0),
       ('9204', 'HR20260101000002', '9003', '2', NOW(), '2', NOW(), 0);

-- 演示看房申请（租户 zhangsan 申请看房）
INSERT INTO hrent_apply_view (id, apply_user_id, house_id, view_time, state, create_by, create_time, update_by, update_time, remake, del_flag)
VALUES ('9301', '3', 'HR20260101000001', '2026-10-20 14:00:00', 0, '3', NOW(), '3', NOW(), NULL, 0);

-- 演示租房申请（租户 zhangsan 申请租房，押金已支付）
INSERT INTO hrent_apply_rent (id, apply_user_id, house_id, start_time, end_time, state, deposit, create_by, create_time, update_by, update_time, remake, del_flag)
VALUES ('9401', '3', 'HR20260101000001', '2026-11-01 00:00:00', '2027-10-31 23:59:59', 3, 1500, '3', NOW(), '3', NOW(), NULL, 0);

-- 演示在租记录
INSERT INTO hrent_house_renting (id, house_id, contract, tenant_id, money, start_time, end_time, create_by, create_time, update_by, update_time, remake, del_flag)
VALUES ('9501', 'HR20260101000001', NULL, '3', 500, '2026-11-01 00:00:00', '2027-10-31 23:59:59', '2', NOW(), '2', NOW(), NULL, 0);

-- 演示租金记录
INSERT INTO hrent_rent (id, renting_id, payable_time, pay_time, tenant_id, state, create_by, create_time, update_by, update_time, remake, del_flag)
VALUES ('9601', '9501', '2026-12-01 00:00:00', NULL, '3', 0, '2', NOW(), '2', NOW(), NULL, 0);

-- 演示站内消息
INSERT INTO sys_message (id, receiver_type, receiver_id, message_name, text, title, state, `system`, create_by, create_time, update_by, update_time, remake, del_flag)
VALUES ('9701', 0, '3', '看房申请提醒', '您有一条看房申请，请尽快处理', '看房申请', 0, '1', '1', NOW(), '1', NOW(), NULL, 0);
