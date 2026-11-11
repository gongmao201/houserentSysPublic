<template>
  <div class="v2-page">
    <el-form :inline="true" size="small">
      <el-form-item label="账期"><el-input v-model="period" clearable placeholder="YYYY-MM" style="width: 130px"></el-input></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部" style="width: 140px">
          <el-option v-for="s in statuses" :label="s.label" :value="s.value" :key="s.value"></el-option>
        </el-select>
      </el-form-item>
      <el-button type="primary" size="small" :loading="loading" @click="loadList">查询</el-button>
      <el-button size="small" @click="generateVisible = true">生成账单</el-button>
      <el-button size="small" @click="loadPending">待确认队列</el-button>
    </el-form>

    <el-table :data="list" border size="small" style="width: 100%">
      <el-table-column prop="period" label="账期" width="100"></el-table-column>
      <el-table-column prop="houseTitle" label="房源" show-overflow-tooltip></el-table-column>
      <el-table-column label="应收(元)" width="120">
        <template slot-scope="scope">¥ {{ fen2yuan(scope.row.amountFen) }}</template>
      </el-table-column>
      <el-table-column label="已收(元)" width="120">
        <template slot-scope="scope">¥ {{ fen2yuan(scope.row.paidFen) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template slot-scope="scope"><el-tag :type="tagType(scope.row.billStatus)">{{ statusLabel(scope.row.billStatus) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="payableDate" label="应缴日" width="120"></el-table-column>
      <el-table-column label="操作" width="240">
        <template slot-scope="scope">
          <el-button size="mini" @click="openDetail(scope.row.id)">明细</el-button>
          <el-button size="mini" type="warning" v-if="canMark(scope.row)" @click="openMark(scope.row)">标记已缴</el-button>
          <el-button size="mini" type="success" v-if="scope.row.billStatus === 'MARKED'" @click="confirm(scope.row.id)">确认到账</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px" background layout="total, prev, pager, next"
                   :total="page.count" :page-size="page.pageSize" :current-page="page.pageNo"
                   @current-change="onPageChange"></el-pagination>
    <el-empty v-if="!loading && list.length === 0" description="暂无账单"></el-empty>

    <el-dialog title="账单明细" :visible.sync="detailVisible" width="600px">
      <div v-if="detail">
        <p><b>账期：</b>{{ detail.period }}　<b>状态：</b>{{ statusLabel(detail.billStatus) }}</p>
        <p><b>应收：</b>¥ {{ fen2yuan(detail.amountFen) }}　<b>已收：</b>¥ {{ fen2yuan(detail.paidFen) }}</p>
        <el-table :data="detail.items || []" size="mini" border>
          <el-table-column prop="itemType" label="项目"></el-table-column>
          <el-table-column label="金额(元)"><template slot-scope="s">¥ {{ fen2yuan(s.row.amountFen) }}</template></el-table-column>
          <el-table-column prop="remark" label="备注"></el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog title="生成账单" :visible.sync="generateVisible" width="440px">
      <el-form :model="genForm" label-width="100px" size="small">
        <el-form-item label="租约 ID"><el-input v-model="genForm.rentingId"></el-input></el-form-item>
        <el-form-item label="账期"><el-input v-model="genForm.period" placeholder="2026-11"></el-input></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="generateVisible = false">取消</el-button><el-button type="primary" @click="generate">生成</el-button></div>
    </el-dialog>

    <el-dialog title="标记已缴（线下转账）" :visible.sync="markVisible" width="460px">
      <el-form :model="markForm" label-width="110px" size="small">
        <el-form-item label="实缴金额(元)"><el-input v-model="markForm.yuan"></el-input></el-form-item>
        <el-form-item label="渠道">
          <el-select v-model="markForm.channel">
            <el-option label="线下微信" value="offline_wechat"></el-option>
            <el-option label="线下支付宝" value="offline_alipay"></el-option>
            <el-option label="线下现金" value="offline_cash"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="凭证路径"><el-input v-model="markForm.voucherPic" placeholder="/images/xxx.jpg"></el-input></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="markVisible = false">取消</el-button><el-button type="primary" @click="markPaid">提交</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { v2, fen2yuan, yuan2fen } from '../../network/v2';

export default {
  name: 'BillManage',
  data() {
    return {
      loading: false,
      period: '',
      status: '',
      list: [],
      page: { pageNo: 1, pageSize: 10, count: 0 },
      statuses: [
        { value: 'UNPAID', label: '待缴' }, { value: 'MARKED', label: '租客已标记' },
        { value: 'PAID', label: '已到账' }, { value: 'OVERDUE', label: '逾期' }
      ],
      detailVisible: false, detail: null,
      generateVisible: false, genForm: { rentingId: '', period: '' },
      markVisible: false, markForm: { id: '', yuan: '', channel: 'offline_wechat', voucherPic: '' }
    };
  },
  mounted() { this.loadList(); },
  methods: {
    fen2yuan: fen2yuan,
    loadList() {
      this.loading = true;
      const params = { pageNo: this.page.pageNo, pageSize: this.page.pageSize };
      if (this.period) { params.period = this.period; }
      if (this.status) { params.status = this.status; }
      v2.myBills(params).then(function (res) {
        this.list = res.list || [];
        this.page.count = res.count || 0;
      }.bind(this)).catch(this.onError).then(function () { this.loading = false; }.bind(this));
    },
    loadPending() {
      this.loading = true;
      v2.pendingBills({ pageNo: 1, pageSize: 50 }).then(function (res) {
        this.list = res.list || [];
        this.page.count = res.count || 0;
        this.$message.info('待确认 ' + this.page.count + ' 条');
      }.bind(this)).catch(this.onError).then(function () { this.loading = false; }.bind(this));
    },
    onPageChange(p) { this.page.pageNo = p; this.loadList(); },
    canMark(row) { return row.billStatus === 'UNPAID' || row.billStatus === 'OVERDUE'; },
    openDetail(id) {
      v2.billDetail(id).then(function (res) { this.detail = res; this.detailVisible = true; }.bind(this)).catch(this.onError);
    },
    openMark(row) {
      this.markForm = { id: row.id, yuan: fen2yuan(row.amountFen), channel: 'offline_wechat', voucherPic: '' };
      this.markVisible = true;
    },
    generate() {
      if (!this.genForm.rentingId || !this.genForm.period) { this.$message.warning('租约 ID 与账期必填'); return; }
      v2.generateBill(this.genForm).then(function (res) {
        this.$message.success('账单已生成，应收 ¥ ' + fen2yuan(res.amountFen));
        this.generateVisible = false;
        this.loadList();
      }.bind(this)).catch(this.onError);
    },
    markPaid() {
      let paidFen;
      try { paidFen = yuan2fen(this.markForm.yuan); } catch (e) { this.$message.error(e.message); return; }
      v2.markPaid(this.markForm.id, {
        paidFen: paidFen, channel: this.markForm.channel, voucherPic: this.markForm.voucherPic || null
      }).then(function () {
        this.$message.success('已标记，等待房东确认到账');
        this.markVisible = false;
        this.loadList();
      }.bind(this)).catch(this.onError);
    },
    confirm(id) {
      v2.confirmBill(id).then(function (res) {
        this.$message.success(res.idempotentHit ? '该账单已确认过（幂等返回）' : '已确认到账');
        this.loadList();
      }.bind(this)).catch(this.onError);
    },
    statusLabel(s) {
      const hit = this.statuses.filter(function (x) { return x.value === s; });
      return hit.length ? hit[0].label : s;
    },
    tagType(s) {
      if (s === 'OVERDUE') return 'danger';
      if (s === 'PAID') return 'success';
      if (s === 'MARKED') return 'warning';
      return '';
    },
    onError(err) {
      console.error('[v2 bill]', err);
      this.$message.error((err && err.message ? err.message : '请求失败') +
        (err && err.traceId ? '（traceId: ' + err.traceId + '）' : ''));
    }
  }
};
</script>

<style scoped>
.v2-page { padding: 12px; }
</style>
