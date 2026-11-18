<template>
  <div class="v2-page">
    <el-form :inline="true" size="small">
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部" style="width: 170px">
          <el-option v-for="s in statuses" :label="s.label" :value="s.value" :key="s.value"></el-option>
        </el-select>
      </el-form-item>
      <el-button type="primary" size="small" :loading="loading" @click="refresh">刷新列表</el-button>
      <el-button size="small" @click="createVisible = true">新建报修</el-button>
    </el-form>

    <el-table :data="list" border size="small" style="width: 100%">
      <el-table-column prop="id" label="工单号" width="180" show-overflow-tooltip></el-table-column>
      <el-table-column prop="houseId" label="房源 ID" width="180" show-overflow-tooltip></el-table-column>
      <el-table-column label="类型" width="120">
        <template slot-scope="scope">{{ typeLabel(scope.row.type) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip></el-table-column>
      <el-table-column prop="status" label="状态" width="160">
        <template slot-scope="scope"><el-tag :type="tagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180"></el-table-column>
      <el-table-column label="操作" width="230">
        <template slot-scope="scope">
          <el-button size="mini" :disabled="actingId === scope.row.id" @click="openDetail(scope.row.id)">详情</el-button>
          <el-button v-for="a in actionsOf(scope.row.status)" :key="a.action" size="mini" type="primary"
                     :disabled="actingId === scope.row.id"
                     @click="doAction(scope.row.id, a.action)">{{ a.label }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination style="margin-top: 12px" background layout="total, prev, pager, next"
                   :total="page.count" :page-size="page.pageSize" :current-page="page.pageNo"
                   @current-change="onPageChange"></el-pagination>
    <el-empty v-if="!loading && list.length === 0" description="暂无报修工单"></el-empty>

    <el-dialog title="工单详情与时间线" :visible.sync="detailVisible" width="640px">
      <div v-if="detail">
        <p><b>状态：</b>{{ statusLabel(detail.status) }}　<b>类型：</b>{{ typeLabel(detail.type) }}</p>
        <p><b>描述：</b>{{ detail.description }}</p>
        <p><b>租客：</b>{{ detail.tenantId }}　<b>房东：</b>{{ detail.landlordId }}</p>
        <el-timeline>
          <el-timeline-item v-for="(log, i) in detail.logs" :key="i" :timestamp="log.createdAt || ''">
            {{ statusLabel(log.fromStatus) || '起点' }} → {{ statusLabel(log.toStatus) }}（{{ log.operatorRole }}）{{ log.remark }}
          </el-timeline-item>
        </el-timeline>
        <p v-if="detail.images && detail.images.length"><b>图片：</b>{{ detail.images.length }} 张</p>
      </div>
    </el-dialog>

    <el-dialog title="新建报修" :visible.sync="createVisible" width="500px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="房源 ID"><el-input v-model="form.houseId"></el-input></el-form-item>
        <el-form-item label="租约 ID"><el-input v-model="form.leaseId"></el-input></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option v-for="t in types" :label="t.label" :value="t.value" :key="t.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="问题描述"><el-input type="textarea" v-model="form.description" :maxlength="1000"></el-input></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="createRepair">提交</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { v2 } from '../../network/v2';
import { onErr, enumLabel, fetchList } from './common';

export default {
  name: 'RepairManage',
  data() {
    return {
      loading: false,
      creating: false,
      actingId: '',
      status: '',
      list: [],
      page: { pageNo: 1, pageSize: 10, count: 0 },
      statuses: [
        { value: 'CREATED', label: '待接单' }, { value: 'ACCEPTED', label: '已接单' },
        { value: 'ON_SITE', label: '上门中' }, { value: 'COMPLETED', label: '已完成' },
        { value: 'ACCEPTED_BY_TENANT', label: '已验收' }, { value: 'CANCELLED', label: '已取消' }
      ],
      types: [
        { value: 'WATER', label: '水暖' }, { value: 'ELECTRIC', label: '电路' },
        { value: 'DOOR_WINDOW', label: '门窗' }, { value: 'APPLIANCE', label: '家电' }, { value: 'OTHER', label: '其他' }
      ],
      actions: {
        CREATED: [{ action: 'accept', label: '接单' }, { action: 'cancel', label: '取消' }],
        ACCEPTED: [{ action: 'arrive', label: '上门' }],
        ON_SITE: [{ action: 'complete', label: '完成' }],
        COMPLETED: [{ action: 'confirm', label: '验收' }]
      },
      detailVisible: false,
      detail: null,
      createVisible: false,
      form: { houseId: '', leaseId: '', type: 'WATER', description: '' }
    };
  },
  mounted() { this.loadList(); },
  methods: {
    //刷新/切换筛选入口：回到第 1 页，避免停在第 2+ 页时筛出空列表
    refresh() {
      this.page.pageNo = 1;
      this.loadList();
    },
    loadList() {
      const params = { pageNo: this.page.pageNo, pageSize: this.page.pageSize };
      if (this.status) { params.status = this.status; }
      fetchList(this, v2.myRepairs, params);
    },
    onPageChange(p) { this.page.pageNo = p; this.loadList(); },
    openDetail(id) {
      v2.repairDetail(id).then(function (res) {
        this.detail = res;
        this.detailVisible = true;
      }.bind(this)).catch(this.onError);
    },
    doAction(id, action) {
      //操作进行中禁用整行按钮，并忽略重复触发
      if (this.actingId) { return; }
      this.actingId = id;
      const payload = {};
      if (action === 'cancel') {
        this.$prompt('请输入取消原因', '取消工单', { inputPattern: /\S+/, inputErrorMessage: '原因不能为空' })
          .then(function (r) { payload.reason = r.value; return v2.repairAction(id, action, payload); }.bind(this))
          .then(function () { this.$message.success('操作成功'); this.loadList(); }.bind(this))
          .catch(this.onError)
          .then(function () { this.actingId = ''; }.bind(this));
        return;
      }
      v2.repairAction(id, action, payload).then(function () {
        this.$message.success('操作成功');
        this.loadList();
      }.bind(this)).catch(this.onError).then(function () { this.actingId = ''; }.bind(this));
    },
    createRepair() {
      if (!this.form.houseId || !this.form.description) { this.$message.warning('房源 ID 与问题描述必填'); return; }
      this.creating = true;
      v2.createRepair(this.form).then(function (res) {
        this.$message.success('报修已创建：' + res.repairId);
        this.createVisible = false;
        this.loadList();
      }.bind(this)).catch(this.onError).then(function () { this.creating = false; }.bind(this));
    },
    actionsOf(status) { return this.actions[status] || []; },
    statusLabel(s) { return enumLabel(this.statuses, s); },
    typeLabel(t) { return enumLabel(this.types, t); },
    tagType(s) {
      if (s === 'CANCELLED') return 'danger';
      if (s === 'ACCEPTED_BY_TENANT') return 'success';
      if (s === 'CREATED') return 'warning';
      return '';
    },
    onError: function (err) { onErr(this, err, 'repair'); }
  }
};
</script>

<style scoped>
.v2-page { padding: 12px; }
</style>
