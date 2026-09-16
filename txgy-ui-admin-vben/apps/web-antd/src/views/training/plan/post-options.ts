import { getSimplePostList } from '#/api/system/post';

import { getJobList } from '../basedata/api';

/** 岗位下拉选项（value 为系统岗位 ID，用于按岗位圈选参训人员） */
export interface PostOption {
  label: string;
  value: number;
}

/**
 * 加载「参训岗位」下拉选项
 *
 * 数据来源（合并去重）：
 * 1. 培训业务岗位工种（基础数据 → 岗位工种 training_job），需已同步为系统岗位，
 *    参见 sql/2026-09-16-training-post-scope-options.sql，label 形如「[OP-02] 防腐涂装工」
 * 2. 其他系统岗位（系统管理 → 岗位管理），保证存量配置仍可选、可回显
 *
 * 说明：未同步到系统岗位的岗位工种不展示 —— 没有系统岗位 ID 无法用于圈选参训人员。
 */
export async function loadPostOptions(): Promise<PostOption[]> {
  const [posts, jobs] = await Promise.all([
    getSimplePostList().catch(() => []),
    getJobList().catch(() => []),
  ]);

  /** 系统岗位：岗位名称 → 岗位 ID（simple-list 不下发 status，故仅按 id/name 过滤） */
  const postMap = new Map<string, number>();
  (posts ?? [])
    .filter((item) => item.name && item.id != null)
    .forEach((item) => {
      postMap.set(item.name as string, item.id as number);
    });

  const options: PostOption[] = [];
  const usedIds = new Set<number>();

  // 1）培训业务岗位工种（按 sort 排序，与基础数据列表一致）
  (jobs ?? [])
    .filter((job) => job.status === 'active' && job.name)
    .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    .forEach((job) => {
      const id = postMap.get(job.name as string);
      if (id === undefined || usedIds.has(id)) {
        return;
      }
      usedIds.add(id);
      options.push({
        label: job.code ? `[${job.code}] ${job.name}` : (job.name as string),
        value: id,
      });
    });

  // 2）其他系统岗位
  (posts ?? [])
    .filter(
      (item) => item.name && item.id != null && !usedIds.has(item.id as number),
    )
    .forEach((item) => {
      usedIds.add(item.id as number);
      options.push({ label: item.name as string, value: item.id as number });
    });

  return options;
}
