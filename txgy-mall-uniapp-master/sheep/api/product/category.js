import request from '@/sheep/request';

const CategoryApi = {
  // 查询分类列表
  getCategoryList: () => {
    return request({
      url: '/product/category/list',
      method: 'GET',
      custom: { scope: 'browse' },
    });
  },
  // 查询分类列表，指定编号
  getCategoryListByIds: (ids) => {
    return request({
      url: '/product/category/list-by-ids',
      method: 'GET',
      params: { ids },
      custom: { scope: 'browse' },
    });
  },
};

export default CategoryApi;
