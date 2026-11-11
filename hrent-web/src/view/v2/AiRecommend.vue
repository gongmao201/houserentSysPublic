<template>
  <div class="v2-page">
    <el-form :model="form" label-width="90px" size="small" style="max-width: 720px">
      <el-form-item label="自然语言">
        <el-input v-model="form.query" placeholder="例如：天河 2500 以内两房 能养猫 近地铁"></el-input>
      </el-form-item>
      <el-form-item label="区域"><el-input v-model="form.filters.region" placeholder="留空则由 query 规则抽取"></el-input></el-form-item>
      <el-form-item label="预算上限(元)"><el-input v-model="budgetYuan" placeholder="留空则不限"></el-input></el-form-item>
      <el-form-item label="户型">
        <el-select v-model="form.filters.rooms" clearable placeholder="不限">
          <el-option v-for="r in [1,2,3,4,5]" :label="r + ' 房'" :value="r" :key="r"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="标签">
        <el-checkbox-group v-model="form.filters.tags">
          <el-checkbox label="pet">养宠</el-checkbox>
          <el-checkbox label="metro">近地铁</el-checkbox>
          <el-checkbox label="elevator">电梯</el-checkbox>
          <el-checkbox label="sunny">采光</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="search">获取推荐</el-button>
        <el-tag style="margin-left: 12px" v-if="result">模式：{{ result.mode }}</el-tag>
      </el-form-item>
    </el-form>

    <el-alert v-if="notice" :title="notice" type="info" show-icon style="margin-bottom: 12px"></el-alert>

    <div v-if="result && result.intent" class="intent">
      解析条件：区域 {{ result.intent.region || '不限' }} / 预算
      {{ result.intent.budgetMax ? fen2yuan(result.intent.budgetMax) + ' 元' : '不限' }} /
      {{ result.intent.rooms || '不限' }} 房 / 标签 {{ (result.intent.tags || []).join(', ') || '无' }}
      （来源 {{ result.intent.source }}）
    </div>

    <el-row :gutter="12" v-if="result">
      <el-col :span="8" v-for="item in (result.list || [])" :key="item.houseId" style="margin-bottom: 12px">
        <el-card shadow="hover">
          <div class="title">{{ item.title || item.houseId }}</div>
          <div class="price">¥ {{ fen2yuan(item.priceFen) }} / 月</div>
          <div class="score">匹配度 {{ item.score }}</div>
          <div class="reason">{{ item.reason }}</div>
          <div style="margin-top: 8px">
            <el-tag v-for="t in (item.hitTags || [])" :key="t" size="mini" style="margin-right: 4px">{{ t }}</el-tag>
          </div>
          <el-button size="mini" style="margin-top: 10px" @click="feedback(item.houseId, 'CLICK')">点此反馈点击</el-button>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="result && (!result.list || result.list.length === 0)" description="没有匹配房源"></el-empty>
  </div>
</template>

<script>
import { v2, fen2yuan, yuan2fen } from '../../network/v2';

export default {
  name: 'AiRecommend',
  data() {
    return {
      loading: false,
      budgetYuan: '',
      form: { query: '', filters: { region: '', budgetMax: null, rooms: null, tags: [] }, pageNo: 1, pageSize: 10 },
      result: null,
      notice: ''
    };
  },
  methods: {
    fen2yuan: fen2yuan,
    search() {
      const payload = {
        query: this.form.query,
        filters: {
          region: this.form.filters.region || null,
          rooms: this.form.filters.rooms || null,
          tags: this.form.filters.tags
        },
        pageNo: this.form.pageNo,
        pageSize: this.form.pageSize
      };
      if (this.budgetYuan !== '' && this.budgetYuan !== null) {
        try { payload.filters.budgetMax = yuan2fen(this.budgetYuan); }
        catch (e) { this.$message.error(e.message); return; }
      }
      this.loading = true;
      this.notice = '';
      v2.recommend(payload).then(function (res) {
        this.result = res;
        this.notice = res._notice || '';
      }.bind(this)).catch(this.onError).then(function () { this.loading = false; }.bind(this));
    },
    feedback(houseId, action) {
      v2.aiFeedback({ houseId: houseId, action: action, query: this.form.query })
        .then(function () { this.$message.success('埋点已记录：' + action); }.bind(this))
        .catch(this.onError);
    },
    onError(err) {
      console.error('[v2 ai]', err);
      this.$message.error((err && err.message ? err.message : '请求失败') +
        (err && err.traceId ? '（traceId: ' + err.traceId + '）' : ''));
    }
  }
};
</script>

<style scoped>
.v2-page { padding: 12px; }
.intent { background: #f5f7fa; padding: 10px; border-radius: 4px; margin-bottom: 14px; font-size: 13px; color: #555; }
.title { font-size: 15px; font-weight: bold; }
.price { color: #c0392b; font-size: 18px; margin: 6px 0; }
.score { color: #888; font-size: 13px; }
.reason { margin-top: 8px; font-size: 13px; line-height: 1.5; }
</style>
