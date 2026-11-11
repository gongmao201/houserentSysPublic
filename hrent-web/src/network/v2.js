import axios from 'axios';
import { base } from '../config';

/**
 * v2 专用请求层（与存量 network/core.js 完全隔离）。
 * 四件事：注入 token、统一解包 {code,message,data,traceId}、600 跳登录、300ms 内同请求合并。
 * baseURL 复用存量 config.js，不新增第二处硬编码地址（待定 T-01/T-08）。
 */
const service = axios.create({
  baseURL: base.baseUrl + '/api/v2',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' }
});

const DEDUPE_MS = 300;
const inflight = new Map();

service.interceptors.request.use(function (config) {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.token = token;
  }
  return config;
}, function (err) {
  return Promise.reject(err);
});

service.interceptors.response.use(function (res) {
  const body = res.data;
  if (!body || typeof body.code === 'undefined') {
    return Promise.reject(new Error('v2 响应格式异常'));
  }
  if (body.code === 200 || body.code === '200') {
    // 后端在特殊场景（如 AI 放宽预算）用顶层 message 给提示，透传到 data 供页面展示
    if (body.message && body.message !== 'ok' && body.data && typeof body.data === 'object' && !Array.isArray(body.data)) {
      body.data._notice = body.message;
    }
    return body.data;
  }
  if (body.code === 600 || body.code === '600') {
    localStorage.removeItem('token');
    window.location.hash = '#/login';
  }
  const e = new Error(body.message || '请求失败');
  e.code = body.code;
  e.traceId = body.traceId;
  return Promise.reject(e);
}, function (err) {
  return Promise.reject(err);
});

function call(method, url, payload, options) {
  const isGet = method === 'get';
  const cfg = { method: method, url: url, timeout: 15000 };
  if (options) {
    Object.keys(options).forEach(function (k) {
      cfg[k] = options[k];
    });
  }
  if (isGet) {
    cfg.params = payload;
  } else {
    cfg.data = payload || {};
  }
  const key = method + '|' + url + '|' + JSON.stringify(payload || {});
  const now = Date.now();
  const hit = inflight.get(key);
  if (hit && now - hit.at < DEDUPE_MS) {
    return hit.promise;
  }
  const promise = service.request(cfg);
  inflight.set(key, { at: now, promise: promise });
  setTimeout(function () {
    const cur = inflight.get(key);
    if (cur && cur.promise === promise) {
      inflight.delete(key);
    }
  }, DEDUPE_MS);
  return promise;
}

function uuid() {
  return 'xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

export const v2 = {
  // ---- 评分 ----
  submitReview: function (data) { return call('post', '/rating/submit', data); },
  houseRating: function (id) { return call('get', '/rating/house/' + encodeURIComponent(id)); },
  landlordRating: function (id) { return call('get', '/rating/landlord/' + encodeURIComponent(id)); },
  replyReview: function (id, data) { return call('post', '/rating/' + encodeURIComponent(id) + '/reply', data); },
  appealReview: function (id, data) { return call('post', '/rating/' + encodeURIComponent(id) + '/appeal', data); },
  auditHouse: function (data) { return call('post', '/rating/audit', data); },

  // ---- 租约 ----
  leaseDetail: function (id) { return call('get', '/lease/' + encodeURIComponent(id)); },
  myLeases: function (params) { return call('get', '/lease/mine', params); },
  terminateLease: function (id, data) { return call('post', '/lease/' + encodeURIComponent(id) + '/terminate', data); },

  // ---- 报修 ----
  createRepair: function (data) {
    // 每次提交生成幂等键；同键重复点击由后端返回首次结果
    return call('post', '/repair', data, { headers: { 'Idempotency-Key': uuid() } });
  },
  myRepairs: function (params) { return call('get', '/repair/mine', params); },
  repairDetail: function (id) { return call('get', '/repair/' + encodeURIComponent(id)); },
  repairAction: function (id, action, data) {
    return call('post', '/repair/' + encodeURIComponent(id) + '/' + action, data || {});
  },

  // ---- 账单 ----
  generateBill: function (data) { return call('post', '/bill/generate', data); },
  myBills: function (params) { return call('get', '/bill/mine', params); },
  pendingBills: function (params) { return call('get', '/bill/pending', params); },
  billDetail: function (id) { return call('get', '/bill/' + encodeURIComponent(id)); },
  markPaid: function (id, data) { return call('post', '/bill/' + encodeURIComponent(id) + '/mark-paid', data); },
  confirmBill: function (id) { return call('post', '/bill/' + encodeURIComponent(id) + '/confirm', {}); },

  // ---- AI ----
  recommend: function (data) { return call('post', '/ai/recommend', data, { timeout: 20000 }); },
  aiReason: function (data) { return call('post', '/ai/reason', data); },
  aiFeedback: function (data) { return call('post', '/ai/feedback', data); },

  // ---- 文件 ----
  upload: function (file) {
    const form = new FormData();
    form.append('file', file);
    // 不手动设置 Content-Type，让 axios/浏览器补 multipart boundary
    return service.request({ method: 'post', url: '/file/upload', data: form, timeout: 60000 });
  }
};

/**金额展示：分 → 元，保留两位；禁止在业务代码里到处 *100 */
export function fen2yuan(fen) {
  if (fen === null || fen === undefined || fen === '') {
    return '0.00';
  }
  return (Number(fen) / 100).toFixed(2);
}

/**金额入参：元 → 分（整数） */
export function yuan2fen(yuan) {
  const n = Number(yuan);
  if (isNaN(n) || n < 0) {
    throw new Error('金额必须是非负数');
  }
  return Math.round(n * 100);
}

export default v2;
