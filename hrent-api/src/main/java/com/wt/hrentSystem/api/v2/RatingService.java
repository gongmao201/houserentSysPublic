package com.wt.hrentSystem.api.v2;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.common.dao.UserDao;
import com.wt.hrentSystem.common.entity.User;
import com.wt.hrentSystem.rating.dao.HouseAuditDao;
import com.wt.hrentSystem.rating.dao.RatingSummaryDao;
import com.wt.hrentSystem.rating.dao.ReviewAppealDao;
import com.wt.hrentSystem.rating.dao.ReviewDao;
import com.wt.hrentSystem.rating.entity.HouseAudit;
import com.wt.hrentSystem.rating.entity.RatingSummary;
import com.wt.hrentSystem.rating.entity.Review;
import com.wt.hrentSystem.rating.entity.ReviewAppeal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 评分 v2（docs/13 §4.2）。
 * 规则：租约 TERMINATED + 实际退租日 30 天内可评；同租约同对象唯一；申诉期间不计入均分；
 * 展示分 = 0.4×平台核验分 + 0.6×贝叶斯平滑用户分（冷启动 C=3、m=4.0，待定 T-06）。
 */
@Service
public class RatingService {

    /**贝叶斯先验强度与先验均值；改口径只动这两个常量 */
    private static final BigDecimal PRIOR_C = new BigDecimal("3");
    private static final BigDecimal PRIOR_M = new BigDecimal("4.0");

    private static final String[] AUDIT_KEYS = {
            "photoReal", "addressVerified", "facilityMatch", "priceReasonable",
            "contractUploaded", "safetyChecked", "depositRuleClear", "noComplaint"
    };
    private static final double[] AUDIT_WEIGHTS = {0.20, 0.20, 0.15, 0.15, 0.10, 0.10, 0.05, 0.05};

    @Resource
    private ReviewDao reviewDao;
    @Resource
    private RatingSummaryDao ratingSummaryDao;
    @Resource
    private HouseAuditDao houseAuditDao;
    @Resource
    private ReviewAppealDao reviewAppealDao;
    @Resource
    private UserDao userDao;
    @Resource
    private V2Mapper v2Mapper;
    @Resource
    private V2Context v2Context;

    public Map<String, Object> submit(Map<String, Object> body, User user) {
        Req.body(body);
        String leaseId = Req.str(body, "leaseId", true, 64);
        String targetType = Req.oneOf(body, "targetType", true, "HOUSE", "LANDLORD");
        String targetId = Req.str(body, "targetId", true, 64);
        BigDecimal score = Req.decimal(body, "score", true);
        if (score.compareTo(BigDecimal.ONE) < 0 || score.compareTo(new BigDecimal("5")) > 0
                || score.stripTrailingZeros().scale() > 1) {
            throw ApiException.bad("score 必须在 1.0~5.0 之间且最多 1 位小数");
        }
        Map<String, Object> dimensions = Req.obj(body, "dimensions");
        String content = Req.str(body, "content", false, 500);
        List<String> images = Req.strList(body, "images", 3);

        // 评价资格：租约已退租 + 30 天窗口 + 本人 + 评价对象与租约一致
        Map<String, Object> lease = v2Mapper.getLeaseById(leaseId);
        if (lease == null) {
            throw ApiException.notFound("租约不存在");
        }
        if (Rows.intOr(lease.get("statusCode"), -1) != 2) {
            throw ApiException.unprocessable("租约未结束，暂不能评价");
        }
        v2Context.assertOwner(user, Rows.str(lease.get("tenantId")));
        String expectedTarget = "HOUSE".equals(targetType)
                ? Rows.str(lease.get("houseId")) : Rows.str(lease.get("landlordId"));
        if (expectedTarget == null || !expectedTarget.equals(targetId)) {
            throw ApiException.unprocessable("评价对象与该租约不匹配");
        }
        Object actualEnd = lease.get("actualEndDate");
        if (actualEnd == null) {
            throw ApiException.unprocessable("缺少实际退租日，评价窗口未开启");
        }
        LocalDate deadline = LocalDate.parse(Rows.day(actualEnd)).plusDays(30);
        if (LocalDate.now(Req.ZONE).isAfter(deadline)) {
            throw ApiException.unprocessable("退租后第 31 天起不可提交评价");
        }

        List<Review> existing = reviewDao.findByLeaseId(leaseId);
        if (existing != null) {
            for (Review r : existing) {
                if (targetType.equals(r.getTargetType()) && targetId.equals(r.getTargetId())) {
                    throw ApiException.conflict("同一租约同一对象只能评价一次");
                }
            }
        }

        Review review = new Review();
        review.preInsert();
        review.setLeaseId(leaseId);
        review.setReviewerId(user.getId());
        review.setTargetType(targetType);
        review.setTargetId(targetId);
        review.setScore(score);
        review.setDimensions(dimensions.isEmpty() ? null : JSON.toJSONString(dimensions));
        review.setContent(content);
        review.setImages(images.isEmpty() ? null : JSON.toJSONString(images));
        review.setStatus("PUBLISHED");
        try {
            reviewDao.insert(review);
        } catch (DuplicateKeyException e) {
            // DB 唯一键兜底：并发双击不落第二条脏数据
            throw ApiException.conflict("同一租约同一对象只能评价一次");
        }

        RatingSummary summary = refreshSummary(targetType, targetId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewId", review.getId());
        result.put("status", "PUBLISHED");
        result.put("displayScore", summary.getDisplayScore());
        return result;
    }

    public Map<String, Object> summary(String targetType, String targetId) {
        RatingSummary s = ratingSummaryDao.getById(targetType, targetId);
        List<Review> all = reviewDao.findByTarget(targetType, targetId);
        List<Review> published = published(all);
        if (s == null) {
            s = refreshSummary(targetType, targetId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("officialScore", s.getOfficialScore());
        result.put("userScore", s.getUserScore());
        result.put("displayScore", s.getDisplayScore());
        result.put("reviewCount", s.getReviewCount() == null ? 0 : s.getReviewCount());
        result.put("isNew", (s.getReviewCount() == null ? 0 : s.getReviewCount()) < 3);
        result.put("dimensions", dimensionAverages(published));
        result.put("reviews", reviewVos(published));
        return result;
    }

    public Map<String, Object> reply(String id, Map<String, Object> body, User user) {
        Req.body(body);
        String content = Req.str(body, "content", true, 100);
        Review review = reviewDao.get(id);
        if (review == null) {
            throw ApiException.notFound("评价不存在");
        }
        v2Context.assertOwner(user, ownerOf(review));
        if (review.getReply() != null && !review.getReply().trim().isEmpty()) {
            throw ApiException.conflict("每条评价只能回复一次");
        }
        Review patch = new Review();
        patch.setId(id);
        patch.setReply(content);
        patch.setReplyAt(new Date());
        patch.setStatus("PUBLISHED");
        patch.preUpdate();
        reviewDao.update(patch);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviewId", id);
        result.put("reply", content);
        result.put("replyAt", V2Time.iso(patch.getReplyAt()));
        return result;
    }

    public Map<String, Object> appeal(String id, Map<String, Object> body, User user) {
        Req.body(body);
        String reason = Req.str(body, "reason", true, 500);
        Review review = reviewDao.get(id);
        if (review == null) {
            throw ApiException.notFound("评价不存在");
        }
        v2Context.assertOwner(user, ownerOf(review));
        if ("APPEALING".equals(review.getStatus())) {
            throw ApiException.conflict("该评价已在申诉中");
        }
        ReviewAppeal appeal = new ReviewAppeal();
        appeal.setId(UUID.randomUUID().toString().replace("-", ""));
        appeal.setReviewId(id);
        appeal.setApplicantId(user.getId());
        appeal.setReason(reason);
        appeal.setStatus("PENDING");
        appeal.setCreateTime(new Date());
        try {
            reviewAppealDao.insert(appeal);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("该评价已提交过申诉");
        }
        Review patch = new Review();
        patch.setId(id);
        patch.setStatus("APPEALING");
        patch.preUpdate();
        reviewDao.update(patch);
        // 申诉期间不计入均分
        refreshSummary(review.getTargetType(), review.getTargetId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealId", appeal.getId());
        result.put("status", "APPEALING");
        return result;
    }

    public Map<String, Object> audit(Map<String, Object> body, User user) {
        if (!v2Context.isAdmin(user)) {
            throw ApiException.forbidden("仅管理员可执行平台核验");
        }
        Req.body(body);
        String houseId = Req.str(body, "houseId", true, 64);
        Map<String, Object> items = Req.obj(body, "items");
        if (items.isEmpty()) {
            throw ApiException.bad("缺少核验项 items");
        }
        BigDecimal score = BigDecimal.ZERO;
        for (int i = 0; i < AUDIT_KEYS.length; i++) {
            if (Req.bool(items, AUDIT_KEYS[i])) {
                score = score.add(BigDecimal.valueOf(AUDIT_WEIGHTS[i] * 5));
            }
        }
        score = score.setScale(2, RoundingMode.HALF_UP);

        HouseAudit audit = new HouseAudit();
        audit.setId(UUID.randomUUID().toString().replace("-", ""));
        audit.setHouseId(houseId);
        audit.setAuditorId(user.getId());
        audit.setPhotoReal(flag(items, "photoReal"));
        audit.setAddressVerified(flag(items, "addressVerified"));
        audit.setFacilityMatch(flag(items, "facilityMatch"));
        audit.setPriceReasonable(flag(items, "priceReasonable"));
        audit.setContractUploaded(flag(items, "contractUploaded"));
        audit.setSafetyChecked(flag(items, "safetyChecked"));
        audit.setDepositRuleClear(flag(items, "depositRuleClear"));
        audit.setNoComplaint(flag(items, "noComplaint"));
        audit.setScore(score);
        audit.setConclusion("8 项加权核验，命中即计权");
        audit.setCreateTime(new Date());
        houseAuditDao.insert(audit);
        refreshSummary("HOUSE", houseId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("houseId", houseId);
        result.put("officialScore", score);
        return result;
    }

    /**重算汇总：只统计 PUBLISHED，APPEALING/HIDDEN 不计入均分 */
    private RatingSummary refreshSummary(String targetType, String targetId) {
        List<Review> published = published(reviewDao.findByTarget(targetType, targetId));
        int n = published.size();
        BigDecimal sum = BigDecimal.ZERO;
        for (Review r : published) {
            if (r.getScore() != null) {
                sum = sum.add(r.getScore());
            }
        }
        BigDecimal userScore = n == 0 ? null
                : sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        BigDecimal officialScore = null;
        if ("HOUSE".equals(targetType)) {
            HouseAudit latest = houseAuditDao.findLatestByHouseId(targetId);
            if (latest != null) {
                officialScore = latest.getScore();
            }
        }
        BigDecimal bayesian = sum.add(PRIOR_C.multiply(PRIOR_M))
                .divide(PRIOR_C.add(BigDecimal.valueOf(n)), 4, RoundingMode.HALF_UP);
        BigDecimal officialPart = officialScore == null ? PRIOR_M : officialScore;
        BigDecimal display = officialPart.multiply(new BigDecimal("0.4"))
                .add(bayesian.multiply(new BigDecimal("0.6")))
                .setScale(1, RoundingMode.HALF_UP);

        RatingSummary s = new RatingSummary();
        s.setTargetType(targetType);
        s.setTargetId(targetId);
        s.setOfficialScore(officialScore);
        s.setUserScore(userScore);
        s.setDisplayScore(display);
        s.setReviewCount(n);
        s.setUpdatedAt(new Date());
        ratingSummaryDao.upsert(s);
        return s;
    }

    private String ownerOf(Review review) {
        return "LANDLORD".equals(review.getTargetType())
                ? review.getTargetId()
                : v2Mapper.findLandlordIdByHouseId(review.getTargetId());
    }

    private List<Review> published(List<Review> all) {
        List<Review> result = new ArrayList<>();
        if (all != null) {
            for (Review r : all) {
                if ("PUBLISHED".equals(r.getStatus())) {
                    result.add(r);
                }
            }
        }
        return result;
    }

    private Map<String, Object> dimensionAverages(List<Review> reviews) {
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Review r : reviews) {
            if (r.getDimensions() == null || r.getDimensions().trim().isEmpty()) {
                continue;
            }
            JSONObject jo = JSON.parseObject(r.getDimensions());
            for (Map.Entry<String, Object> e : jo.entrySet()) {
                if (!(e.getValue() instanceof Number)) {
                    continue;
                }
                BigDecimal v = new BigDecimal(e.getValue().toString());
                sums.merge(e.getKey(), v, BigDecimal::add);
                counts.merge(e.getKey(), 1, Integer::sum);
            }
        }
        Map<String, Object> avg = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : sums.entrySet()) {
            int c = counts.getOrDefault(e.getKey(), 1);
            avg.put(e.getKey(), e.getValue().divide(BigDecimal.valueOf(c), 1, RoundingMode.HALF_UP));
        }
        return avg;
    }

    private List<Map<String, Object>> reviewVos(List<Review> reviews) {
        List<Map<String, Object>> result = new ArrayList<>();
        int limit = Math.min(reviews.size(), 10);
        for (int i = 0; i < limit; i++) {
            Review r = reviews.get(i);
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", r.getId());
            vo.put("userName", maskName(userDao.get(r.getReviewerId())));
            vo.put("score", r.getScore());
            vo.put("content", r.getContent());
            vo.put("images", r.getImages() == null ? Collections.emptyList()
                    : JSON.parseArray(r.getImages(), String.class));
            vo.put("reply", r.getReply());
            vo.put("createdAt", V2Time.iso(r.getCreateDate()));
            result.add(vo);
        }
        return result;
    }

    /**日志与响应均不展示完整姓名/手机号 */
    private String maskName(User u) {
        if (u == null || u.getName() == null || u.getName().trim().isEmpty()) {
            return "匿名";
        }
        String name = u.getName().trim();
        return name.substring(0, 1) + "**";
    }

    private Integer flag(Map<String, Object> items, String key) {
        return Req.bool(items, key) ? 1 : 0;
    }
}
