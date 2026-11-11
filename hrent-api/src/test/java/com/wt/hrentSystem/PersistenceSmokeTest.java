package com.wt.hrentSystem;

import com.wt.hrentSystem.ai.dao.AiFeedbackDao;
import com.wt.hrentSystem.ai.dao.HouseEmbeddingDao;
import com.wt.hrentSystem.ai.entity.AiFeedback;
import com.wt.hrentSystem.bill.dao.BillItemDao;
import com.wt.hrentSystem.common.dao.UserRoleDao;
import com.wt.hrentSystem.lease.dao.LeaseLogDao;
import com.wt.hrentSystem.rating.dao.HouseAuditDao;
import com.wt.hrentSystem.rating.dao.RatingSummaryDao;
import com.wt.hrentSystem.rating.dao.ReviewAppealDao;
import com.wt.hrentSystem.rating.dao.ReviewDao;
import com.wt.hrentSystem.rating.entity.Review;
import com.wt.hrentSystem.rating.entity.ReviewAppeal;
import com.wt.hrentSystem.repair.dao.RepairImageDao;
import com.wt.hrentSystem.repair.dao.RepairLogDao;
import com.wt.hrentSystem.repair.dao.RepairOrderDao;
import com.wt.hrentSystem.repair.entity.RepairOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 只读冒烟：只调 select，不写数据。
 * 目的：确认 12 个 Dao 的 XML 语句都能被 MyBatis 解析，不抛 BindingException。
 */
@SpringBootTest
public class PersistenceSmokeTest {

    @Autowired private UserRoleDao userRoleDao;
    @Autowired private LeaseLogDao leaseLogDao;
    @Autowired private HouseAuditDao houseAuditDao;
    @Autowired private ReviewDao reviewDao;
    @Autowired private ReviewAppealDao reviewAppealDao;
    @Autowired private RatingSummaryDao ratingSummaryDao;
    @Autowired private RepairOrderDao repairOrderDao;
    @Autowired private RepairImageDao repairImageDao;
    @Autowired private RepairLogDao repairLogDao;
    @Autowired private BillItemDao billItemDao;
    @Autowired private HouseEmbeddingDao houseEmbeddingDao;
    @Autowired private AiFeedbackDao aiFeedbackDao;

    @Test
    public void allDaosCanExecute() {
        assertNotNull(userRoleDao.findByUserId("1"));
        assertNotNull(leaseLogDao.findByLeaseId("9501"));
        assertNotNull(houseAuditDao.findByHouseId("x"));
        assertNotNull(reviewDao.findList(new Review()));
        assertNotNull(reviewDao.findAllList());
        assertNotNull(reviewAppealDao.findList(new ReviewAppeal()));
        assertNotNull(ratingSummaryDao.findTopByType("HOUSE", 5));
        assertNotNull(repairOrderDao.findList(new RepairOrder()));
        assertNotNull(repairOrderDao.findAllList());
        assertNotNull(repairImageDao.findByRepairId("x", null));
        assertNotNull(repairLogDao.findByRepairId("x"));
        assertNotNull(billItemDao.findByRentId("9601"));
        // 单对象查询允许返回 null，只验证语句能执行
        houseEmbeddingDao.getByHouseId("x");
        assertNotNull(aiFeedbackDao.findList(new AiFeedback()));
        System.out.println("PERSISTENCE_SMOKE_OK");
    }
}
