export const base = {
  // 用相对路径而不是写死的主机地址：
  //   开发模式由 config/index.js 的 proxyTable 转发到后端，生产模式与后端同源
  //   （后端把 dist 挂在 /hrent/web/ 下），两种模式都不再硬编码 host/port。
  //   改动原因见 docs/19 待定项 T-01/T-08。
  baseUrl: "/hrent",
  picLocal: "/hrent/images",
  //腾讯地图秘钥
  key: "<YOUR_QQMAP_KEY>",
  //腾讯地图访问获取位置的接口
  getLocationUrl: "https://apis.map.qq.com/ws/location/v1/ip"
};
