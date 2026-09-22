import axiosInstance from '@/api/axios'

/**
 * MES 数据源。
 *
 * 这里刻意不走大屏的「数据源」配置项，因为三维场景需要同时消费 7 个接口，
 * 而大屏的数据源是「一个组件一个请求」。组件自己在预览页轮询更直接，
 * token 与租户头由 @/api/axios 的请求拦截器统一补齐。
 */

export interface MesSnapshot {
  workshops: any[]
  workstations: any[]
  machinery: any[]
  routeProcess: any[]
  tasks: any[]
  andon: any[]
  summary: any
}

/** 分页接口取 list，数组接口原样返回 */
const pageList = (res: any): any[] => {
  const body = res && res.data !== undefined ? res.data : res
  if (Array.isArray(body)) return body
  return (body && body.list) || []
}

/** 非分页接口取 data */
const payload = (res: any): any => (res && res.data !== undefined ? res.data : res)

const safe = async (label: string, fn: () => Promise<any>, fallback: any) => {
  try {
    return await fn()
  } catch (error) {
    console.warn(`[MesWorkshop3D] ${label} 请求失败`, error)
    return fallback
  }
}

export const fetchMesSnapshot = async (routeId = 1): Promise<MesSnapshot> => {
  const [workshops, workstations, machinery, routeProcess, tasks, andon, summary] = await Promise.all([
    safe('车间', () => axiosInstance.get('/mes/md-workshop/page?pageNo=1&pageSize=100'), null),
    safe('工位', () => axiosInstance.get('/mes/md-workstation/page?pageNo=1&pageSize=200'), null),
    safe('设备', () => axiosInstance.get('/mes/dv/machinery/page?pageNo=1&pageSize=200'), null),
    safe('工艺路线工序', () => axiosInstance.get(`/mes/pro/route-process/list-by-route?routeId=${routeId}`), null),
    safe('生产任务', () => axiosInstance.get('/mes/pro/task/page?pageNo=1&pageSize=200'), null),
    safe('安灯记录', () => axiosInstance.get('/mes/pro/andon-record/page?pageNo=1&pageSize=100'), null),
    safe('首页汇总', () => axiosInstance.get('/mes/home-statistics/summary'), null)
  ])

  return {
    workshops: pageList(workshops),
    workstations: pageList(workstations),
    machinery: pageList(machinery),
    routeProcess: pageList(routeProcess),
    tasks: pageList(tasks),
    andon: pageList(andon),
    summary: payload(summary) || {}
  }
}