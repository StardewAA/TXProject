import type { PageParam, PageResult } from '@vben/request';

import { requestClient } from '#/api/request';

export namespace SystemMailAccountApi {
  /** 邮箱账号 */
  export interface MailAccount {
    id: number;
    mail: string;
    username: string;
    /** 密码（授权码）。新增必填；修改时留空表示不修改（查询接口不返回密码） */
    password?: string;
    /** 是否已配置密码（授权码），由后端回传 */
    passwordConfigured?: boolean;
    host: string;
    port: number;
    sslEnable: boolean;
    starttlsEnable: boolean;
    status: number;
    createTime: Date;
    remark: string;
  }
}

/** 查询邮箱账号列表 */
export function getMailAccountPage(params: PageParam) {
  return requestClient.get<PageResult<SystemMailAccountApi.MailAccount>>(
    '/system/mail-account/page',
    { params },
  );
}

/** 查询邮箱账号详情 */
export function getMailAccount(id: number) {
  return requestClient.get<SystemMailAccountApi.MailAccount>(
    `/system/mail-account/get?id=${id}`,
  );
}

/** 新增邮箱账号 */
export function createMailAccount(data: SystemMailAccountApi.MailAccount) {
  return requestClient.post('/system/mail-account/create', data);
}

/** 修改邮箱账号 */
export function updateMailAccount(data: SystemMailAccountApi.MailAccount) {
  return requestClient.put('/system/mail-account/update', data);
}

/** 删除邮箱账号 */
export function deleteMailAccount(id: number) {
  return requestClient.delete(`/system/mail-account/delete?id=${id}`);
}

/** 批量删除邮箱账号 */
export function deleteMailAccountList(ids: number[]) {
  return requestClient.delete(
    `/system/mail-account/delete-list?ids=${ids.join(',')}`,
  );
}

/** 获得邮箱账号精简列表 */
export function getSimpleMailAccountList() {
  return requestClient.get<SystemMailAccountApi.MailAccount[]>(
    '/system/mail-account/simple-list',
  );
}
