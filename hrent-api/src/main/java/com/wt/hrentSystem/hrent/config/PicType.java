package com.wt.hrentSystem.hrent.config;

/**
 * 图片类型常量（对应 hrent_pic.type）
 * 说明：原始发布包缺失该类，UserService/HouseResourceService 依赖，此为重建实现。
 */
public class PicType {

    /** 小程序轮播图 */
    public static final Integer Banner = 1;
    /** 按钮 */
    public static final Integer Button = 2;
    /** 公用 */
    public static final Integer Common = 3;
    /** 系统 */
    public static final Integer SystemPic = 4;
    /** 房源 */
    public static final Integer HouseResource = 5;
    /** 用户 */
    public static final Integer User = 6;

    private PicType() {
    }
}
