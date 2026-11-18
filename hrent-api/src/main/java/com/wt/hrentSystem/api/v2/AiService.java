package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.ai.dao.AiFeedbackDao;
import com.wt.hrentSystem.ai.entity.AiFeedback;
import com.wt.hrentSystem.ai.llm.AiGateway;
import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.common.entity.User;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 推荐 v2（docs/13 §4.6、docs/00 §3.1.0 D18）。
 * 管道：规则抽取 → 不足 3 字段时模型补意图（local→deepseek）→ 标签打分检索 →
 * 模板理由 / SSE 理由增强；模型全挂仍 200，退化为纯规则。
 */
@Service
public class AiService {

    private static final Pattern BUDGET = Pattern.compile("(\\d{2,7})\\s*元?以内|预算\\s*(\\d{2,7})");
    private static final Pattern ROOMS = Pattern.compile("([一二两三四五六1-9])\\s*房");
    private static final Pattern REGION = Pattern.compile("([\\u4e00-\\u9fa5]{2,6}区)");

    private static final Map<String, String> TAG_WORDS = new HashMap<>();

    static {
        TAG_WORDS.put("pet", "养宠");
        TAG_WORDS.put("metro", "近地铁");
        TAG_WORDS.put("elevator", "电梯");
        TAG_WORDS.put("sunny", "采光");
        TAG_WORDS.put("new", "新上");
    }

    @Resource
    private V2Mapper v2Mapper;
    @Resource
    private AiFeedbackDao aiFeedbackDao;
    @Resource
    private AiGateway aiGateway;

    public Map<String, Object> recommend(Map<String, Object> body) {
        Req.body(body);
        String query = Req.str(body, "query", false, 500);
        Map<String, Object> filters = Req.obj(body, "filters");
        int pageNo = Req.pageNo(Req.integer(body, "pageNo", false));
        int pageSize = Req.pageSize(Req.integer(body, "pageSize", false));

        String region = Req.str(filters, "region", false, 64);
        Long budgetMax = Req.fen(filters, "budgetMax", false);
        Integer rooms = Req.integer(filters, "rooms", false);
        List<String> tags = new ArrayList<>(Req.strList(filters, "tags", 10));

        // 中文 query 的规则抽取：只补空字段，不覆盖结构化 filters
        if (query != null) {
            Matcher bm = BUDGET.matcher(query);
            if (budgetMax == null && bm.find()) {
                String num = bm.group(1) == null ? bm.group(2) : bm.group(1);
                budgetMax = Long.parseLong(num) * 100L;
            }
            Matcher rm = ROOMS.matcher(query);
            if (rooms == null && rm.find()) {
                rooms = chineseNumber(rm.group(1));
            }
            Matcher dm = REGION.matcher(query);
            if (region == null && dm.find()) {
                region = dm.group(1);
            }
            if (query.contains("养猫") || query.contains("宠物") || query.contains("养宠")) {
                addTag(tags, "pet");
            }
            if (query.contains("地铁")) {
                addTag(tags, "metro");
            }
            if (query.contains("电梯")) {
                addTag(tags, "elevator");
            }
            if (query.contains("采光") || query.contains("朝南")) {
                addTag(tags, "sunny");
            }
        }

        // 规则命中不足 3 个字段才调模型；模型失败继续用规则结果，绝不 500
        String source = "rule";
        if (query != null && filledFieldCount(region, budgetMax, rooms, tags) < 3) {
            AiGateway.IntentOutcome outcome = aiGateway.extractIntent(query);
            if (outcome.fromModel()) {
                Map<String, Object> extra = outcome.getIntent();
                if (region == null && extra.get("region") != null) {
                    region = String.valueOf(extra.get("region"));
                }
                if (budgetMax == null && extra.get("budgetMax") != null) {
                    budgetMax = ((Number) extra.get("budgetMax")).longValue();
                }
                if (rooms == null && extra.get("rooms") != null) {
                    rooms = ((Number) extra.get("rooms")).intValue();
                }
                Object modelTags = extra.get("tags");
                if (modelTags instanceof List) {
                    for (Object tag : (List<?>) modelTags) {
                        addTag(tags, String.valueOf(tag));
                    }
                }
                source = outcome.getProvider();
            }
        }

        Set<String> keywords = new LinkedHashSet<>();
        for (String tag : tags) {
            keywords.add(TAG_WORDS.containsKey(tag) ? TAG_WORDS.get(tag) : tag);
        }
        List<String> tagKeywords = new ArrayList<>(keywords);

        int offset = (pageNo - 1) * pageSize;
        List<Map<String, Object>> rows = v2Mapper.findHousesForRecommend(
                region, budgetMax, rooms, tagKeywords, offset, pageSize);
        int total = v2Mapper.countHousesForRecommend(region, budgetMax, rooms, tagKeywords);
        String message = "ok";

        // 无结果时后端放宽预算 20% 再查一次，前端只负责展示
        if ((rows == null || rows.isEmpty()) && budgetMax != null) {
            Long relaxed = Math.round(budgetMax * 1.2);
            rows = v2Mapper.findHousesForRecommend(region, relaxed, rooms, tagKeywords, offset, pageSize);
            total = v2Mapper.countHousesForRecommend(region, relaxed, rooms, tagKeywords);
            budgetMax = relaxed;
            message = "没有完全匹配的房源，已为你放宽预算 20%";
        }

        List<Map<String, Object>> list = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                list.add(toRecommendItem(row, region, rooms, budgetMax, tagKeywords));
            }
        }

        Map<String, Object> intent = new LinkedHashMap<>();
        intent.put("region", region);
        intent.put("budgetMax", budgetMax);
        intent.put("rooms", rooms);
        intent.put("tags", tags);
        intent.put("source", source);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "rule".equals(source) ? "rule" : "llm");
        data.put("intent", intent);
        data.put("list", list);
        data.put("total", total);
        return resultWithMessage(data, message);
    }

    /**
     * 理由补全：先给模板，再尝试 local→deepseek 批量增强；模型全挂时模板不变。
     * 请求内最多 20 个 houseId，模型一次批处理，避免 N 次超时串联。
     */
    public Map<String, Object> reason(Map<String, Object> body) {
        Req.body(body);
        List<String> ids = Req.strList(body, "houseIds", 20);
        List<Map<String, Object>> items = templateReasonItems(ids);
        AiGateway.ReasonOutcome outcome = reasonOutcome(ids);
        if (!outcome.getReasons().isEmpty()) {
            for (Map<String, Object> item : items) {
                String id = String.valueOf(item.get("houseId"));
                String enhanced = outcome.getReasons().get(id);
                if (enhanced != null) {
                    item.put("reason", enhanced);
                    item.put("source", outcome.getProvider());
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("mode", outcome.getReasons().isEmpty() ? "rule" : "llm");
        data.put("source", outcome.getProvider());
        return data;
    }

    /**模板理由，SSE 首帧用：不调模型，先让前端有内容可展示 */
    public List<Map<String, Object>> templateReasonItems(List<String> ids) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> brief = v2Mapper.getHouseBrief(id);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("houseId", id);
            item.put("reason", brief == null ? "房源信息暂不可用" : templateReason(brief, null, null, null, null));
            item.put("source", "rule");
            items.add(item);
        }
        return items;
    }

    /**调用降级链批量生成理由，SSE 增强与 /reason 共用同一入口 */
    public AiGateway.ReasonOutcome reasonOutcome(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new AiGateway.ReasonOutcome(Collections.emptyMap(), "rule");
        }
        List<Map<String, Object>> briefs = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> brief = v2Mapper.getHouseBrief(id);
            if (brief == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("houseId", id);
            item.put("title", Rows.str(brief.get("title")));
            item.put("region", Rows.str(brief.get("region")));
            item.put("houseType", Rows.str(brief.get("houseType")));
            item.put("priceFen", Rows.lng(brief.get("priceFen")));
            briefs.add(item);
        }
        return aiGateway.reasons(briefs);
    }

    public Map<String, Object> feedback(Map<String, Object> body, User user) {
        Req.body(body);
        String houseId = Req.str(body, "houseId", true, 64);
        String action = Req.oneOf(body, "action", true, "IMPRESSION", "CLICK", "APPLY");
        Integer position = Req.integer(body, "position", false);
        if (position != null && (position < 1 || position > 10)) {
            throw ApiException.bad("position 必须在 1~10 之间");
        }
        String query = Req.str(body, "query", false, 500);
        String mode = Req.oneOf(body, "mode", false, "rule", "llm");
        AiFeedback fb = new AiFeedback();
        fb.setId(UUID.randomUUID().toString().replace("-", ""));
        fb.setUserId(user.getId());
        fb.setQuery(maskSensitive(query));
        fb.setHouseId(houseId);
        fb.setAction(action);
        fb.setPosition(position);
        fb.setMode(mode == null ? "rule" : mode);
        fb.setCreateTime(new Date());
        aiFeedbackDao.insert(fb);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("feedbackId", fb.getId());
        data.put("action", action);
        return data;
    }

    private Map<String, Object> toRecommendItem(Map<String, Object> row, String region,
                                                 Integer rooms, Long budgetMax, List<String> keywords) {
        String tagNames = Rows.str(row.get("tagNames"));
        List<String> hit = new ArrayList<>();
        if (tagNames != null) {
            for (String kw : keywords) {
                if (tagNames.contains(kw)) {
                    hit.add(kw);
                }
            }
        }
        Long priceFen = Rows.lng(row.get("priceFen"));
        double score = 0.5 + hit.size() * 0.1;
        if (budgetMax != null && budgetMax > 0 && priceFen != null) {
            score += 0.2 * (1 - Math.min(priceFen / (double) budgetMax, 1.0));
        }
        score = Math.min(0.99, Math.max(0.01, score));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("houseId", Rows.str(row.get("houseId")));
        item.put("title", Rows.str(row.get("title")));
        item.put("priceFen", priceFen);
        item.put("score", BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
        item.put("hitTags", hit);
        item.put("reason", templateReason(row, region, rooms, budgetMax, hit));
        return item;
    }

    private String templateReason(Map<String, Object> row, String region, Integer rooms,
                                  Long budgetMax, List<String> hit) {
        StringBuilder sb = new StringBuilder();
        if (region != null && !region.isEmpty()) {
            sb.append(region);
        }
        if (rooms != null) {
            sb.append(rooms).append("房");
        }
        if (sb.length() > 0) {
            sb.append("，");
        }
        Long priceFen = Rows.lng(row.get("priceFen"));
        sb.append(priceFen == null ? "价格待议"
                : new BigDecimal(priceFen).divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP) + " 元");
        if (hit != null && !hit.isEmpty()) {
            sb.append("，命中「").append(String.join("」「", hit)).append("」");
        } else {
            sb.append("，匹配你的结构化条件");
        }
        return sb.toString();
    }

    /**返回体里附带 message 的方式：由 Controller 读这个键并设置顶层 message */
    private Map<String, Object> resultWithMessage(Map<String, Object> data, String message) {
        data.put("_message", message);
        return data;
    }

    public static String takeMessage(Map<String, Object> data) {
        Object m = data.remove("_message");
        return m == null ? "ok" : String.valueOf(m);
    }

    /**docs/00 §3.1：规则命中 ≥3 字段直接检索，不调模型；标签按每项计数 */
    private int filledFieldCount(String region, Long budgetMax, Integer rooms, List<String> tags) {
        int count = 0;
        if (region != null && !region.isEmpty()) {
            count++;
        }
        if (budgetMax != null) {
            count++;
        }
        if (rooms != null) {
            count++;
        }
        count += tags == null ? 0 : tags.size();
        return count;
    }

    private void addTag(List<String> tags, String tag) {
        if (!tags.contains(tag)) {
            tags.add(tag);
        }
    }

    private Integer chineseNumber(String raw) {
        switch (raw) {
            case "一":
            case "1":
                return 1;
            case "二":
            case "两":
            case "2":
                return 2;
            case "三":
            case "3":
                return 3;
            case "四":
            case "4":
                return 4;
            case "五":
            case "5":
                return 5;
            case "六":
            case "6":
                return 6;
            default:
                return null;
        }
    }

    /**埋点 query 脱敏：手机号一律掩码，日志/库中不存明文 */
    private String maskSensitive(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2");
    }
}
