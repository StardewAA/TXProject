import { requestClient } from '#/api/request';

export namespace HrmRecruitPostTypeApi {
  /** 招聘职位类型 */
  export interface RecruitPostType {
    id?: number; // 职位类型编号
    name: string; // 类型名称
    parentId?: number; // 父类型编号
    sort?: number; // 排序
    status?: number; // 状态
    createTime?: Date; // 创建时间
  }
}

/** 查询招聘职位类型列表 */
export function getRecruitPostTypeList(params?: { status?: number }) {
  return requestClient.get<HrmRecruitPostTypeApi.RecruitPostType[]>(
    '/hrm/recruit/post-type/list',
    { params },
  );
}

/** 查询招聘职位类型详情 */
export function getRecruitPostType(id: number) {
  return requestClient.get<HrmRecruitPostTypeApi.RecruitPostType>(
    `/hrm/recruit/post-type/get?id=${id}`,
  );
}

/** 新增招聘职位类型 */
export function createRecruitPostType(
  data: HrmRecruitPostTypeApi.RecruitPostType,
) {
  return requestClient.post<number>('/hrm/recruit/post-type/create', data);
}

/** 修改招聘职位类型 */
export function updateRecruitPostType(
  data: HrmRecruitPostTypeApi.RecruitPostType,
) {
  return requestClient.put<boolean>('/hrm/recruit/post-type/update', data);
}

/** 修改招聘职位类型状态 */
export function updateRecruitPostTypeStatus(data: {
  id: number;
  status: number;
}) {
  return requestClient.put<boolean>(
    '/hrm/recruit/post-type/update-status',
    data,
  );
}

/** 删除招聘职位类型 */
export function deleteRecruitPostType(id: number) {
  return requestClient.delete<boolean>(
    `/hrm/recruit/post-type/delete?id=${id}`,
  );
}
