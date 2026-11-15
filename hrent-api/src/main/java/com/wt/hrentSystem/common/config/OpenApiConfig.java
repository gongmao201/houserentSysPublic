package com.wt.hrentSystem.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI 3 文档配置（springdoc）。
 *
 * spec 覆盖全部 Controller：存量接口（/user、/hrent、/sysCommon…）与 v2（/api/v2/**）。
 * 鉴权统一声明为请求头 token；实际路径会带 context-path /hrent，故 servers 用相对地址。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hrentOpenAPI() {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag().name("认证").description("登录态：退出登录、令牌吊销"));
        tags.add(new Tag().name("v2-评分").description("评价提交、评分汇总、回复、申诉、平台核验"));
        tags.add(new Tag().name("v2-租约").description("租约详情、我的租约、退租确认"));
        tags.add(new Tag().name("v2-报修").description("工单创建、列表、详情时间线、状态流转"));
        tags.add(new Tag().name("v2-账单").description("按月生成、我的账单、对账、缴费确认"));
        tags.add(new Tag().name("v2-AI").description("规则推荐、理由模板、反馈埋点"));
        tags.add(new Tag().name("v2-文件").description("图片白名单上传"));
        tags.add(new Tag().name("存量-用户").description("注册、登录、个人信息、图片上传"));
        tags.add(new Tag().name("存量-房源").description("房源、预约、收藏、租赁申请"));
        tags.add(new Tag().name("存量-系统").description("字典、菜单、消息、公共配置"));

        return new OpenAPI()
                .info(new Info()
                        .title("hrent-api 房屋租赁系统接口")
                        .version("v1.0 + v2")
                        .description("存量接口与 v2 接口统一契约。鉴权：请求头 token（登录接口返回的 JWT）。"
                                + "v2 响应体为 {code, message, data, traceId}，HTTP 恒 200；金额单位为分。")
                        .contact(new Contact().name("hrent-api")))
                .servers(servers())
                .tags(tags)
                .components(new Components().addSecuritySchemes("token",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("token")
                                .description("登录接口返回的 JWT；v2 也接受 Authorization: Bearer <token>")))
                .addSecurityItem(new SecurityRequirement().addList("token"));
    }

    private List<Server> servers() {
        List<Server> servers = new ArrayList<>();
        servers.add(new Server().url("/hrent").description("当前部署（server.servlet.context-path=/hrent）"));
        return servers;
    }
}
