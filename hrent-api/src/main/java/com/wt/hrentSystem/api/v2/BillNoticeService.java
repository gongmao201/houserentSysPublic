package com.wt.hrentSystem.api.v2;

import com.wt.hrentSystem.api.dao.V2Mapper;
import com.wt.hrentSystem.common.dao.MessageDao;
import com.wt.hrentSystem.common.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 逾期站内信（docs/00 §3.4、docs/03 W7）。
 * 事务内完成：1) 条件更新 bill_status=OVERDUE + overdue_notified_at；2) 写 sys_message。
 * 条件更新是幂等闸门：多实例/重复调度只有一个能拿到 affected=1，不会重复发信。
 */
@Service
public class BillNoticeService {

    private static final Logger log = LoggerFactory.getLogger(BillNoticeService.class);

    @Resource
    private V2Mapper v2Mapper;
    @Resource
    private MessageDao messageDao;

    @Transactional(rollbackFor = Exception.class)
    public boolean notifyOverdue(Map<String, Object> bill) {
        String billId = Rows.str(bill.get("id"));
        if (billId == null) {
            return false;
        }
        int marked = v2Mapper.markOverdueNotified(billId);
        if (marked == 0) {
            //已被其他实例/上一次调度处理
            return false;
        }
        String tenantId = Rows.str(bill.get("tenantId"));
        if (tenantId == null || tenantId.isEmpty()) {
            log.warn("逾期账单缺少 tenant_id，跳过发信 billId=" + billId);
            return false;
        }
        String period = Rows.str(bill.get("period"));
        String title = Rows.str(bill.get("houseTitle"));
        long amountFen = Rows.longOr(bill.get("amountFen"), 0L);
        BigDecimal yuan = BigDecimal.valueOf(amountFen).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Message message = new Message();
        message.setId(UUID.randomUUID().toString().replace("-", ""));
        message.setReceiverType("0");
        message.setReceiverId(tenantId);
        message.setMessageName("BILL_OVERDUE");
        message.setTitle("租金逾期提醒");
        message.setText("您租住的「" + (title == null ? "房源" : title) + "」"
                + (period == null ? "" : "账期 " + period + " ")
                + "租金 ¥" + yuan.toPlainString() + " 已逾期，请尽快在账单页补缴。");
        message.setState(0);
        //sys_message.system 是 VARCHAR(2)，沿用存量系统标识 "0"（不能写 hrent，会超长）
        message.setSystem("0");
        message.setCreateDate(new Date());
        message.setUpdateDate(message.getCreateDate());
        message.setDelFlag(0);
        messageDao.insert(message);
        return true;
    }
}
