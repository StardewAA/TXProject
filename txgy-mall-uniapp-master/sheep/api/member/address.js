import request from '@/sheep/request';

const AddressApi = {
  getLocationCapability: () => request({
    url: '/member/address/location-capability', method: 'GET',
    // 只读取全局能力状态，不包含地图密钥，也不应被失效登录态或当前租户阻断。
    custom: { scope: 'platform', isToken: false, showLoading: false, showError: false },
  }),
  // 获得用户收件地址列表
  getAddressList: () => {
    return request({
      url: '/member/address/list',
      method: 'GET',
      custom: { auth: true, showLoading: false },
    });
  },
  // 平台结算沿用平台账号来源会员的地址。该接口本身仍是旧会员接口，
  // tenant-id 只能取自已核验的平台会话，绝不取页面参数或当前浏览租户。
  getPlatformAddressList: () => request({
    url: '/member/address/list',
    method: 'GET',
    custom: { scope: 'platform-account', auth: true, showLoading: false },
  }),
  // 创建用户收件地址
  createAddress: (data) => {
    return request({
      url: '/member/address/create',
      method: 'POST',
      data,
      custom: { auth: true, showSuccess: true, successMsg: '保存成功' },
    });
  },
  // 更新用户收件地址
  updateAddress: (data) => {
    return request({
      url: '/member/address/update',
      method: 'PUT',
      data,
      custom: { auth: true, showSuccess: true, successMsg: '更新成功' },
    });
  },
  // 获得用户收件地址
  getAddress: (id) => {
    return request({
      url: '/member/address/get',
      method: 'GET',
      params: { id },
      custom: { auth: true, showLoading: false },
    });
  },
  // 删除用户收件地址
  deleteAddress: (id) => {
    return request({
      url: '/member/address/delete',
      method: 'DELETE',
      params: { id },
      custom: { auth: true },
    });
  },
};

export default AddressApi;
