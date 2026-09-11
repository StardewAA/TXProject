import request from '@/sheep/request';

const AfterSaleApi = {
  // 获得售后分页
  getAfterSalePage: (params) => {
    return request({
      url: `/trade/after-sale/page`,
      method: 'GET',
      params,
      custom: {
        auth: true,
        showLoading: false,
      },
    });
  },
  // 创建售后
  createAfterSale: (data) => {
    return request({
      url: `/trade/after-sale/create`,
      method: 'POST',
      data,
      custom: { auth: true },
    });
  },
  // 获得售后
  getAfterSale: (id) => {
    return request({
      url: `/trade/after-sale/get`,
      method: 'GET',
      params: {
        id,
      },
      custom: { auth: true },
    });
  },
  // 取消售后
  cancelAfterSale: (id) => {
    return request({
      url: `/trade/after-sale/cancel`,
      method: 'DELETE',
      params: {
        id,
      },
      custom: { auth: true },
    });
  },
  // 获得售后日志列表
  getAfterSaleLogList: (afterSaleId) => {
    return request({
      url: `/trade/after-sale-log/list`,
      method: 'GET',
      params: {
        afterSaleId,
      },
      custom: { auth: true },
    });
  },
  // 退回货物
  deliveryAfterSale: (data) => {
    return request({
      url: `/trade/after-sale/delivery`,
      method: 'PUT',
      data,
      custom: { auth: true },
    });
  }
};

export default AfterSaleApi;
