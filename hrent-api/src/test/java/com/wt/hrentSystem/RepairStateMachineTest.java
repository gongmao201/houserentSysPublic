package com.wt.hrentSystem;

import com.wt.hrentSystem.api.v2.RepairService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报修状态机（docs/14 FR-REPAIR-02/03/04）：6 状态，任意非终态可取消。
 * 终态仅 ACCEPTED_BY_TENANT / CANCELLED。
 */
class RepairStateMachineTest {

    @Test
    void forwardPathIsLegal() {
        assertTrue(RepairService.canTransit("CREATED", "ACCEPTED"));
        assertTrue(RepairService.canTransit("ACCEPTED", "ON_SITE"));
        assertTrue(RepairService.canTransit("ON_SITE", "COMPLETED"));
        assertTrue(RepairService.canTransit("COMPLETED", "ACCEPTED_BY_TENANT"));
    }

    @Test
    void anyNonTerminalCanCancel() {
        assertTrue(RepairService.canTransit("CREATED", "CANCELLED"));
        assertTrue(RepairService.canTransit("ACCEPTED", "CANCELLED"));
        assertTrue(RepairService.canTransit("ON_SITE", "CANCELLED"));
        assertTrue(RepairService.canTransit("COMPLETED", "CANCELLED"));
    }

    @Test
    void skipAndRollbackAreRejected() {
        assertFalse(RepairService.canTransit("CREATED", "ON_SITE"));
        assertFalse(RepairService.canTransit("CREATED", "COMPLETED"));
        assertFalse(RepairService.canTransit("ACCEPTED", "COMPLETED"));
        assertFalse(RepairService.canTransit("COMPLETED", "CREATED"));
        assertFalse(RepairService.canTransit("ON_SITE", "ACCEPTED"));
    }

    @Test
    void terminalStatesHaveNoNext() {
        assertTrue(RepairService.isTerminal("ACCEPTED_BY_TENANT"));
        assertTrue(RepairService.isTerminal("CANCELLED"));
        assertFalse(RepairService.isTerminal("COMPLETED"));
        assertEquals("[]", RepairService.allowedNext("CANCELLED").toString());
        assertEquals("[ACCEPTED_BY_TENANT, CANCELLED]", RepairService.allowedNext("COMPLETED").toString());
    }

    @Test
    void allThirtySixStatusPairsMatchTruthTable() {
        String[] all = {"CREATED", "ACCEPTED", "ON_SITE", "COMPLETED", "ACCEPTED_BY_TENANT", "CANCELLED"};
        java.util.Map<String, java.util.Set<String>> expected = new java.util.HashMap<>();
        expected.put("CREATED", new java.util.HashSet<>(java.util.Arrays.asList("ACCEPTED", "CANCELLED")));
        expected.put("ACCEPTED", new java.util.HashSet<>(java.util.Arrays.asList("ON_SITE", "CANCELLED")));
        expected.put("ON_SITE", new java.util.HashSet<>(java.util.Arrays.asList("COMPLETED", "CANCELLED")));
        expected.put("COMPLETED", new java.util.HashSet<>(java.util.Arrays.asList("ACCEPTED_BY_TENANT", "CANCELLED")));
        expected.put("ACCEPTED_BY_TENANT", java.util.Collections.emptySet());
        expected.put("CANCELLED", java.util.Collections.emptySet());

        for (String from : all) {
            for (String to : all) {
                assertEquals(expected.get(from).contains(to), RepairService.canTransit(from, to),
                        "状态对 " + from + " → " + to + " 判定与真值表不一致");
            }
        }
    }
}
