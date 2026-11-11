<template>
  <div class="v2-page">
    <el-form :inline="true" size="small">
      <el-form-item label="评价对象">
        <el-select v-model="targetType" style="width: 120px">
          <el-option label="房源" value="HOUSE"></el-option>
          <el-option label="房东" value="LANDLORD"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="对象 ID">
        <el-input v-model="targetId" clearable placeholder="房源 ID / 房东 ID"></el-input>
      </el-form-item>
      <el-button type="primary" size="small" :loading="loading" @click="loadSummary">查询评分</el-button>
    </el-form>

    <el-row :gutter="12" v-if="summary">
      <el-col :span="6"><el-card><div class="metric-label">平台核验分</div><div class="metric-value">{{ show(summary.officialScore) }}</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="metric-label">用户均分</div><div class="metric-value">{{ show(summary.userScore) }}</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="metric-label">展示分</div><div class="metric-value">{{ show(summary.displayScore) }}</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="metric-label">有效评价数</div><div class="metric-value">{{ summary.reviewCount || 0 }}</div></el-card></el-col>
    </el-row>
    <div style="margin: 10px 0">
      <el-tag v-if="summary && summary.isNew" type="success">新上（评价不足 3 条，不展示低分）</el-tag>
      <span v-if="summary && summary.dimensions">维度：{{ formatDimensions(summary.dimensions) }}</span>
    </div>

    <el-table :data="reviews" border size="small" style="width: 100%">
      <el-table-column prop="userName" label="用户" width="100"></el-table-column>
      <el-table-column prop="score" label="评分" width="80"></el-table-column>
      <el-table-column prop="content" label="内容" show-overflow-tooltip></el-table-column>
      <el-table-column prop="reply" label="房东回复" show-overflow-tooltip></el-table-column>
      <el-table-column prop="createdAt" label="时间" width="180"></el-table-column>
    </el-table>
    <el-empty v-if="!loading && reviews.length === 0" description="暂无评价"></el-empty>

    <el-tabs style="margin-top: 18px">
      <el-tab-pane label="提交评价">
        <el-form :model="reviewForm" label-width="100px" size="small" style="max-width: 620px">
          <el-form-item label="租约 ID"><el-input v-model="reviewForm.leaseId" placeholder="需已退租且 30 天内"></el-input></el-form-item>
          <el-form-item label="对象类型">
            <el-select v-model="reviewForm.targetType"><el-option label="房源" value="HOUSE"></el-option><el-option label="房东" value="LANDLORD"></el-option></el-select>
          </el-form-item>
          <el-form-item label="对象 ID"><el-input v-model="reviewForm.targetId"></el-input></el-form-item>
          <el-form-item label="评分"><el-input-number v-model="reviewForm.score" :min="1" :max="5" :step="0.5"></el-input-number></el-form-item>
          <el-form-item label="评价内容"><el-input type="textarea" v-model="reviewForm.content" :maxlength="500"></el-input></el-form-item>
          <el-form-item><el-button type="primary" @click="submitReview">提交</el-button></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="回复 / 申诉">
        <el-form :inline="true" size="small">
          <el-form-item label="评价 ID"><el-input v-model="reviewId"></el-input></el-form-item>
          <el-form-item label="内容"><el-input v-model="opContent"></el-input></el-form-item>
          <el-button @click="reply">房东回复</el-button>
          <el-button type="warning" @click="appeal">发起申诉</el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="平台核验（管理员）">
        <el-form :inline="true" size="small">
          <el-form-item label="房源 ID"><el-input v-model="auditHouseId"></el-input></el-form-item>
        </el-form>
        <el-checkbox-group v-model="auditChecked">
          <el-checkbox v-for="k in auditKeys" :label="k" :key="k">{{ auditLabels[k] }}</el-checkbox>
        </el-checkbox-group>
        <div style="margin-top: 12px"><el-button type="primary" size="small" @click="submitAudit">提交核验</el-button></div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { v2 } from '../../network/v2';

export default {
  name: 'RatingManage',
  data() {
    return {
      loading: false,
      targetType: 'HOUSE',
      targetId: '',
      summary: null,
      reviews: [],
      reviewForm: { leaseId: '', targetType: 'HOUSE', targetId: '', score: 5, content: '' },
      reviewId: '',
      opContent: '',
      auditHouseId: '',
      auditChecked: [],
      auditKeys: ['photoReal', 'addressVerified', 'facilityMatch', 'priceReasonable', 'contractUploaded', 'safetyChecked', 'depositRuleClear', 'noComplaint'],
      auditLabels: {
        photoReal: '照片真实', addressVerified: '地址可核验', facilityMatch: '设施一致', priceReasonable: '价格合理',
        contractUploaded: '合同已上传', safetyChecked: '消防安全', depositRuleClear: '押付规则', noComplaint: '无投诉'
      }
    };
  },
  methods: {
    show(v) { return (v === null || v === undefined) ? '—' : Number(v).toFixed(1); },
    formatDimensions(d) {
      const keys = Object.keys(d || {});
      if (!keys.length) return '暂无维度分';
      return keys.map(function (k) { return k + ' ' + d[k]; }).join(' / ');
    },
    loadSummary() {
      if (!this.targetId) { this.$message.warning('请先填写对象 ID'); return; }
      this.loading = true;
      const req = this.targetType === 'HOUSE' ? v2.houseRating(this.targetId) : v2.landlordRating(this.targetId);
      req.then(function (res) {
        this.summary = res;
        this.reviews = res.reviews || [];
      }.bind(this)).catch(this.onError).then(function () { this.loading = false; }.bind(this));
    },
    submitReview() {
      v2.submitReview(this.reviewForm).then(function (res) {
        this.$message.success('评价已提交，展示分 ' + this.show(res.displayScore));
        this.targetType = this.reviewForm.targetType;
        this.targetId = this.reviewForm.targetId;
        this.loadSummary();
      }.bind(this)).catch(this.onError);
    },
    reply() {
      if (!this.reviewId || !this.opContent) { this.$message.warning('请填写评价 ID 与回复内容'); return; }
      v2.replyReview(this.reviewId, { content: this.opContent })
        .then(function () { this.$message.success('回复成功'); }.bind(this)).catch(this.onError);
    },
    appeal() {
      if (!this.reviewId || !this.opContent) { this.$message.warning('请填写评价 ID 与申诉理由'); return; }
      v2.appealReview(this.reviewId, { reason: this.opContent })
        .then(function () { this.$message.success('申诉已提交，期间不计入均分'); this.loadSummary(); }.bind(this))
        .catch(this.onError);
    },
    submitAudit() {
      if (!this.auditHouseId) { this.$message.warning('请填写房源 ID'); return; }
      const items = {};
      this.auditKeys.forEach(function (k) { items[k] = false; });
      this.auditChecked.forEach(function (k) { items[k] = true; });
      v2.auditHouse({ houseId: this.auditHouseId, items: items })
        .then(function (res) { this.$message.success('核验分 ' + this.show(res.officialScore)); }.bind(this))
        .catch(this.onError);
    },
    onError(err) {
      console.error('[v2 rating]', err);
      this.$message.error((err && err.message ? err.message : '请求失败') +
        (err && err.traceId ? '（traceId: ' + err.traceId + '）' : ''));
    }
  }
};
</script>

<style scoped>
.v2-page { padding: 12px; }
.metric-label { color: #888; font-size: 13px; }
.metric-value { font-size: 24px; font-weight: bold; margin-top: 6px; }
</style>
