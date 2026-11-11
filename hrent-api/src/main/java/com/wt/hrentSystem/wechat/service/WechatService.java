package com.wt.hrentSystem.wechat.service;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wt.hrentSystem.common.config.ResponseCodeCanstants;
import com.wt.hrentSystem.common.dao.UserDao;
import com.wt.hrentSystem.common.entity.ResponseResult;
import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.common.util.MD5Utils;
import com.wt.hrentSystem.common.util.TokenUtils;
import com.wt.hrentSystem.common.util.UserUtils;
import com.wt.hrentSystem.wechat.entity.WechatUser;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序登录服务
 * 说明：原始发布包缺失该类，WeChatController 依赖，此为重建实现。
 * 登录链路：wx.login code -> jscode2session 换取 openid -> 查询/新建用户 -> 签发 token。
 * appId/appSecret 通过 application.yml 的 wechat.app-id / wechat.app-secret 配置，
 * 未配置时返回明确错误（不会伪造登录成功）。
 */
@Service
public class WechatService {

    @Resource
    private UserDao userDao;

    @Value("${wechat.app-id:}")
    private String appId;

    @Value("${wechat.app-secret:}")
    private String appSecret;

    /**
     * 微信小程序登录
     */
    @Transactional
    public ResponseResult login(WechatUser wechatUser) {
        if (StringUtils.isBlank(appId) || StringUtils.isBlank(appSecret)) {
            return new ResponseResult(ResponseCodeCanstants.FAILED, "微信登录未配置：请在 application.yml 配置 wechat.app-id / wechat.app-secret");
        }
        if (wechatUser == null || StringUtils.isBlank(wechatUser.getCode())) {
            return new ResponseResult(ResponseCodeCanstants.FAILED, "登录凭证 code 不能为空");
        }
        String openId = code2session(wechatUser.getCode());
        if (openId == null) {
            return new ResponseResult(ResponseCodeCanstants.FAILED, "微信登录失败：code 无效或已过期");
        }
        User user = userDao.findUserByOpenid(openId);
        if (user == null) {
            user = buildNewWechatUser(wechatUser, openId);
            userDao.insert(user);
        }
        String token = TokenUtils.sign(user);
        Map<String, Object> data = new HashMap<>(4);
        data.put("token", token);
        data.put("loginUser", user);
        return new ResponseResult(ResponseCodeCanstants.SUCCESS, data, "登录成功");
    }

    /**
     * 获取用户手机号
     * 说明：解密手机号需要 encryptedData + iv + session_key，当前 WechatUser 实体未承载这些字段，
     * 此处返回 null，由调用方感知未获取到手机号（不做伪造）。
     */
    public String getUserNumber(WechatUser wechatUser) {
        return null;
    }

    private User buildNewWechatUser(WechatUser wechatUser, String openId) {
        User user = new User();
        user.setOpenId(openId);
        user.setName(StringUtils.isBlank(wechatUser.getName()) ? "微信用户" : wechatUser.getName());
        user.setLoginName("wx_" + openId.substring(0, Math.min(10, openId.length())));
        try {
            user.setPassword(MD5Utils.EncoderByMd5(openId));
        } catch (Exception e) {
            user.setPassword(openId);
        }
        user.setRole(0);
        user.setRoleStr("租户");
        User defaultUser = new User();
        defaultUser.setId(UserUtils.DEFAULT_USER_ID);
        user.setCurrentUser(defaultUser);
        user.preInsert();
        return user;
    }

    private String code2session(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + appId
                + "&secret=" + appSecret
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        try {
            String body = HttpUtil.get(url, 5000);
            JSONObject json = JSON.parseObject(body);
            return json.getString("openid");
        } catch (Exception e) {
            System.err.println("[WechatService] jscode2session 调用失败: " + e.getMessage());
            return null;
        }
    }
}
