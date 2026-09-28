import { baseUrl, apiPath } from '@/sheep/config';
import request from '@/sheep/request';
import { prepareTenantUpload, isTenantRequestCurrent } from '@/sheep/helper/tenant-runtime';
import { createTenantUploader } from '@/sheep/helper/tenant-upload.mjs';

const uploadFile = createTenantUploader({
  prepare: prepareTenantUpload, isCurrent: isTenantRequestCurrent,
  upload: options => uni.uploadFile({ ...options, url: baseUrl + apiPath + '/infra/file/upload' }),
  showLoading: () => uni.showLoading({ title: '上传中' }),
  hideLoading: () => uni.hideLoading(),
  subscribe: listener => { uni.$on('mall:session-changed', listener); return () => uni.$off('mall:session-changed', listener); },
});

const FileApi = {
  // 上传文件
  uploadFile,

  // 获取文件预签名地址
  getFilePresignedUrl: (name, directory, tenantSnapshot) => {
    return request({
      url: '/infra/file/presigned-url',
      method: 'GET',
      params: {
        name,
        directory,
      },
      custom: { auth: true, tenantSnapshot },
    });
  },

  // 创建文件
  createFile: (data, tenantSnapshot) => {
    return request({
      url: '/infra/file/create', // 请求的 URL
      method: 'POST', // 请求方法
      data: data, // 要发送的数据
      custom: { auth: true, tenantSnapshot },
    });
  },
};

export default FileApi;
