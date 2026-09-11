import request from '@/sheep/request';

const PointApi = {
  // 获得用户积分记录分页
  getPointRecordPage: (params) => {
    const source = params && typeof params === 'object' ? params : {};
    const query = { ...source };
    if (query.addStatus === undefined) {
      delete query.addStatus
    }
    const queryString = Object.keys(query)
      .map((key) => encodeURIComponent(key) + '=' + encodeURIComponent(query[key]))
      .join('&');
    return request({
      url: `/member/point/record/page?${queryString}`,
      method: 'GET',
      custom: { auth: true, showLoading: false },
    });
  }
};

export default PointApi;
