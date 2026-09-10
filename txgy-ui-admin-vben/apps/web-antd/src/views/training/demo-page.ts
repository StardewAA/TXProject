/**
 * 培训子模块演示数据源
 *
 * 后端课程(course)/培训班(plan)已就绪，其余培训子模块（资源中心、学员学习中心、
 * 考试与认证、直播与讲师体系、VR 实训、校企合作、数据统计）的后端接口尚未交付，
 * 页面数据层统一使用本工具维护本地演示数据，保证页面可运行、可操作、不报错。
 * 后端接口就绪后：仅需将各模块 api.ts 中的实现替换为 requestClient 请求即可，
 * 页面（index.vue / data.ts / modules/*）无需改动。
 */
export interface DemoPageResult<T> {
  list: T[];
  total: number;
}

export interface DemoStore<T extends { id?: number }> {
  /** 全部行 */
  list: () => T[];
  /** 本地分页查询 */
  page: (
    params: Record<string, any>,
    match?: (row: T, form: Record<string, any>) => boolean,
  ) => DemoPageResult<T>;
  /** 新增（自动分配自增 id，插入头部） */
  add: (row: Omit<T, 'id'>) => T;
  /** 编辑（按 id 覆盖） */
  update: (row: T) => void;
  /** 局部更新 */
  patch: (id: number, data: Partial<T>) => void;
  /** 删除 */
  remove: (id: number) => void;
}

/** 创建一个内存演示数据源 */
export function createDemoStore<T extends { id?: number }>(seed: T[]): DemoStore<T> {
  let rows: T[] = seed.map((item) => ({ ...item }));

  const list = () => rows;

  const page = (
    params: Record<string, any>,
    match?: (row: T, form: Record<string, any>) => boolean,
  ) => {
    const pageNo = params.pageNo ?? 1;
    const pageSize = params.pageSize ?? 10;
    const { pageNo: _pn, pageSize: _ps, ...form } = params;
    const filtered = match ? rows.filter((row) => match(row, form)) : rows;
    const total = filtered.length;
    return {
      list: filtered.slice((pageNo - 1) * pageSize, pageNo * pageSize),
      total,
    };
  };

  const nextId = () => rows.reduce((max, row) => Math.max(max, row.id ?? 0), 0) + 1;

  const add = (row: Omit<T, 'id'>) => {
    const current = { ...row, id: nextId() } as T;
    rows = [current, ...rows];
    return current;
  };

  const update = (row: T) => {
    rows = rows.map((item) => (item.id === row.id ? { ...item, ...row } : item));
  };

  const patch = (id: number, data: Partial<T>) => {
    rows = rows.map((item) => (item.id === id ? { ...item, ...data } : item));
  };

  const remove = (id: number) => {
    rows = rows.filter((item) => item.id !== id);
  };

  return { list, page, add, update, patch, remove };
}
