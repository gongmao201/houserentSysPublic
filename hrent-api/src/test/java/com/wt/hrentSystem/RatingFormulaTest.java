package com.wt.hrentSystem;

import com.wt.hrentSystem.api.v2.RatingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 评分展示分公式回归（W5 验收：冷启动边界）。
 * display = 0.4×official + 0.6×贝叶斯(C=3, m=4.0)；official 为空时权重回落 1.0。
 */
class RatingFormulaTest {

    @Test
    void coldStartWithoutAuditIsFour() {
        //0 评价、无核验：display = 贝叶斯 (3×4 + 0)/(3+0) = 4.0
        assertEquals(new BigDecimal("4.0"),
                RatingService.computeDisplayScore(null, BigDecimal.ZERO, 0));
    }

    @Test
    void coldStartWithAuditUsesOfficial() {
        //0 评价、核验 5.00：0.4×5 + 0.6×4.0 = 4.4
        assertEquals(new BigDecimal("4.4"),
                RatingService.computeDisplayScore(new BigDecimal("5.00"), BigDecimal.ZERO, 0));
    }

    @Test
    void threeFiveStarsConvergesUpward() {
        //3 条 5 星、无核验：贝叶斯 4.5，official 回落 4.0 → 0.4×4.0 + 0.6×4.5 = 4.3
        assertEquals(new BigDecimal("4.3"),
                RatingService.computeDisplayScore(null, new BigDecimal("15"), 3));
    }

    @Test
    void auditAndReviewAreWeighted() {
        //3 条 5 星 + 核验 5.00：0.4×5 + 0.6×4.5 = 4.7
        assertEquals(new BigDecimal("4.7"),
                RatingService.computeDisplayScore(new BigDecimal("5.00"), new BigDecimal("15"), 3));
    }

    @Test
    void lowScoresPullDownButNotToZero() {
        //3 条 1 星：贝叶斯 2.5，official 回落 4.0 → 0.4×4.0 + 0.6×2.5 = 3.1
        assertEquals(new BigDecimal("3.1"),
                RatingService.computeDisplayScore(null, new BigDecimal("3"), 3));
    }

    @Test
    void nullSumIsTreatedAsZero() {
        assertEquals(new BigDecimal("4.0"),
                RatingService.computeDisplayScore(null, null, 0));
    }
}
