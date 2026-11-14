package com.wt.hrentSystem;

import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.api.v2.BillNoticeService;
import com.wt.hrentSystem.api.v2.BillService;
import com.wt.hrentSystem.bill.dao.BillItemDao;
import com.wt.hrentSystem.bill.entity.BillItem;
import com.wt.hrentSystem.common.dao.MessageDao;
import com.wt.hrentSystem.common.entity.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账单 W7：按月生成 + 逾期站内信（不连库，用 Mockito 校验分支与参数）。
 */
@ExtendWith(MockitoExtension.class)
class BillScheduleTest {

    @Mock
    private V2Mapper v2Mapper;
    @Mock
    private BillItemDao billItemDao;
    @Mock
    private BillNoticeService billNoticeService;
    @Mock
    private MessageDao messageDao;
    @InjectMocks
    private BillService billService;
    @InjectMocks
    private BillNoticeService billNoticeServiceReal;

    @Test
    void monthlyGenerateCreatesBillAndRentItem() {
        Map<String, Object> renting = new LinkedHashMap<>();
        renting.put("rentingId", "r1");
        renting.put("tenantId", "t1");
        renting.put("moneyFen", 250000L);
        when(v2Mapper.findRentingsForMonthlyBill("2026-11", "2026-11-01", "2026-11-30"))
                .thenReturn(Collections.singletonList(renting));
        when(v2Mapper.findBillIdByRentingPeriod("r1", "2026-11")).thenReturn(null);
        when(v2Mapper.insertBill(any(), eq("r1"), eq("2026-11"), eq(250000L),
                eq("2026-11-05"), eq("t1"), eq("system"))).thenReturn(1);
        when(billItemDao.insert(any(BillItem.class))).thenReturn(1);

        Map<String, Object> result = billService.monthlyGenerate(YearMonth.of(2026, 11), "system");

        assertEquals(1, result.get("generated"));
        assertEquals(0, result.get("failed"));
        ArgumentCaptor<BillItem> item = ArgumentCaptor.forClass(BillItem.class);
        verify(billItemDao).insert(item.capture());
        assertEquals("RENT", item.getValue().getItemType());
        assertEquals(250000L, item.getValue().getAmountFen().longValue());
    }

    @Test
    void monthlyGenerateSkipsExistingPeriod() {
        Map<String, Object> renting = new LinkedHashMap<>();
        renting.put("rentingId", "r1");
        when(v2Mapper.findRentingsForMonthlyBill("2026-11", "2026-11-01", "2026-11-30"))
                .thenReturn(Collections.singletonList(renting));
        when(v2Mapper.findBillIdByRentingPeriod("r1", "2026-11")).thenReturn("existing");

        Map<String, Object> result = billService.monthlyGenerate(YearMonth.of(2026, 11), "system");
        assertEquals(0, result.get("generated"));
        assertEquals(1, result.get("skipped"));
    }

    @Test
    void scanOverdueCountsNotified() {
        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("id", "b1");
        when(v2Mapper.findOverdueBillCandidates(10)).thenReturn(Collections.singletonList(bill));
        when(billNoticeService.notifyOverdue(bill)).thenReturn(true);

        Map<String, Object> result = billService.scanOverdue(10);
        assertEquals(1, result.get("notified"));
        assertEquals(0, result.get("failed"));
    }

    @Test
    void notifyOverdueWritesMessageOnlyWhenMarkWins() {
        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("id", "b1");
        bill.put("tenantId", "t1");
        bill.put("period", "2026-10");
        bill.put("amountFen", 258000L);
        bill.put("houseTitle", "天河区某小区 3 栋 501");
        when(v2Mapper.markOverdueNotified("b1")).thenReturn(1);
        when(messageDao.insert(any(Message.class))).thenReturn(1);

        boolean sent = billNoticeServiceReal.notifyOverdue(bill);

        assertTrue(sent);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageDao).insert(captor.capture());
        Message msg = captor.getValue();
        assertEquals("BILL_OVERDUE", msg.getMessageName());
        assertEquals("t1", msg.getReceiverId());
        assertEquals("租金逾期提醒", msg.getTitle());
        assertEquals("0", msg.getSystem(), "system 列只有 VARCHAR(2)，必须用短标识");
        assertTrue(msg.getText().contains("2026-10"));
        assertTrue(msg.getText().contains("2580.00"));
    }

    @Test
    void notifyOverdueIsIdempotentWhenAlreadyNotified() {
        Map<String, Object> bill = new LinkedHashMap<>();
        bill.put("id", "b1");
        when(v2Mapper.markOverdueNotified("b1")).thenReturn(0);

        boolean sent = billNoticeServiceReal.notifyOverdue(bill);
        assertEquals(false, sent);
    }
}
