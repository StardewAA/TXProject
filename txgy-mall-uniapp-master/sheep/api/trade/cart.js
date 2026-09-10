import request from '@/sheep/request';

const CartApi = {
  addCart: (data) => {
    return request({
      url: '/trade/cart/add',
      method: 'POST',
      data: data,
      custom: {
        auth: true,
        showSuccess: true,
        successMsg: '已添加到购物车~',
      }
    });
  },
  updateCartCount: (data) => {
    return request({
      url: '/trade/cart/update-count',
      method: 'PUT',
      data: data,
      custom: { auth: true },
    });
  },
  updateCartSelected: (data) => {
    return request({
      url: '/trade/cart/update-selected',
      method: 'PUT',
      data: data,
      custom: { auth: true },
    });
  },
  deleteCart: (ids) => {
    return request({
      url: '/trade/cart/delete',
      method: 'DELETE',
      params: {
        ids
      },
      custom: { auth: true },
    });
  },
  getCartList: () => {
    return request({
      url: '/trade/cart/list',
      method: 'GET',
      custom: {
        showLoading: false,
        auth: true,
      },
    });
  },
};

export default CartApi;
