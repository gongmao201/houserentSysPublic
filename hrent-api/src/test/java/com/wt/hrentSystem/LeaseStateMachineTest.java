package com.wt.hrentSystem;

import com.wt.hrentSystem.api.v2.LeaseService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租约状态机矩阵（docs/14 FR-LEASE-02）：4 状态 16 组合，合法仅 4 条。
 * 合法：0→1 到期、0→2 退租、0→3 解约、1→2 补退租；其余 12 条必须全部拒绝。
 */
class LeaseStateMachineTest {

    private static final int ACTIVE = 0;
    private static final int EXPIRED = 1;
    private static final int TERMINATED = 2;
    private static final int CANCELLED = 3;

    @Test
    void legalTransitions() {
        assertTrue(LeaseService.canTransit(ACTIVE, EXPIRED));
        assertTrue(LeaseService.canTransit(ACTIVE, TERMINATED));
        assertTrue(LeaseService.canTransit(ACTIVE, CANCELLED));
        assertTrue(LeaseService.canTransit(EXPIRED, TERMINATED));
    }

    @Test
    void illegalTransitionsAreAllRejected() {
        int[][] illegal = {
                {ACTIVE, ACTIVE},
                {EXPIRED, ACTIVE}, {EXPIRED, EXPIRED}, {EXPIRED, CANCELLED},
                {TERMINATED, ACTIVE}, {TERMINATED, EXPIRED}, {TERMINATED, TERMINATED},
                {TERMINATED, CANCELLED},
                {CANCELLED, ACTIVE}, {CANCELLED, EXPIRED}, {CANCELLED, TERMINATED},
                {CANCELLED, CANCELLED}
        };
        for (int[] pair : illegal) {
            assertFalse(LeaseService.canTransit(pair[0], pair[1]),
                    "非法迁移被放行：" + pair[0] + " → " + pair[1]);
        }
    }

    @Test
    void allowedNextIsExposedForFrontend() {
        assertEquals("[EXPIRED, TERMINATED, CANCELLED]", LeaseService.allowedNext(ACTIVE).toString());
        assertEquals("[TERMINATED]", LeaseService.allowedNext(EXPIRED).toString());
        assertEquals("[]", LeaseService.allowedNext(TERMINATED).toString());
        assertEquals("[]", LeaseService.allowedNext(CANCELLED).toString());
    }
}
