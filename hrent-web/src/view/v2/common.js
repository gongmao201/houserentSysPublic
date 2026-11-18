/**
 * v2 四个页面（评分/账单/报修/AI推荐）的公共逻辑抽取：
 * onError / loadList / statusLabel 不再逐字重复，各页面以薄封装方法引用，
 * 模板与调用处的方法名保持不变。
 */

/**统一错误提示；el-prompt 主动取消（'cancel'/'close'）不作为接口错误弹出 */
export function onErr(vm, err, tag) {
  if (err === 'cancel' || err === 'close') { return; }
  console.error('[v2 ' + (tag || 'page') + ']', err);
  vm.$message.error((err && err.message ? err.message : '请求失败') +
    (err && err.traceId ? '（traceId: ' + err.traceId + '）' : ''));
}

/**枚举 → 中文标签；list 为 [{value,label}]，无匹配时原样返回 */
export function enumLabel(list, value) {
  const hit = (list || []).filter(function (x) { return x.value === value; });
  return hit.length ? hit[0].label : value;
}

/**列表统一加载：置 loading → 回填 list/count → 结束后复位 loading（错误走 vm.onError） */
export function fetchList(vm, api, params) {
  vm.loading = true;
  return api(params).then(function (res) {
    vm.list = res.list || [];
    vm.page.count = res.count || 0;
  }).catch(vm.onError).then(function () {
    vm.loading = false;
  });
}
