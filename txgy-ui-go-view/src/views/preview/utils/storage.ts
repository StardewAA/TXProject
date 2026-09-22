import { getSessionStorage, fetchRouteParamsLocation, httpErrorHandle, JSONParse } from '@/utils'
import { ResultEnum } from '@/enums/httpEnum'
import { StorageEnum } from '@/enums/storageEnum'
import { ChartEditStorage } from '@/store/modules/chartEditStore/chartEditStore.d'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { fetchProjectApi } from '@/api/path'

const chartEditStore = useChartEditStore()

export interface ChartEditStorageType extends ChartEditStorage {
  id: string
}

// 根据路由 id 获取存储数据的信息
export const getSessionStorageInfo = async () => {
  const id = fetchRouteParamsLocation()
  const storageList: ChartEditStorageType[] = getSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST)
  // 是否本地预览
  if (!storageList || storageList.findIndex(e => e.id === id.toString()) === -1) {
    // 接口调用
    const res = await fetchProjectApi({ id: id })
    // data 为空 = 项目不存在 / 已被删除。这种返回是正常的 200 + code:0，只是 data 为 null，
    // 直接解构会抛 TypeError；preview 页面在 <suspense> 里，Promise 一 reject 整页就白屏。
    // 所以这里返回 undefined，把"提示 + 跳回我的项目"交给调用方处理。
    if (res && res.code === ResultEnum.SUCCESS && res.data) {
      const { content, status } = res.data
      if (status === 1) {
        // 跳转未发布页
        return { isRelease: false }
      }
      const chartEitStorageType = content as unknown as ChartEditStorageType
      chartEditStore.editCanvasConfig = chartEitStorageType.editCanvasConfig
      chartEditStore.requestGlobalConfig = chartEitStorageType.requestGlobalConfig
      chartEditStore.componentList = chartEitStorageType.componentList
      return { ...JSONParse(content), id }
    } else if (!res || res.code !== ResultEnum.SUCCESS) {
      httpErrorHandle()
    }
  } else {
    // 本地读取
    for (let i = 0; i < storageList.length; i++) {
      if (id.toString() === storageList[i]['id']) {
        const { editCanvasConfig, requestGlobalConfig, componentList } = storageList[i]
        chartEditStore.editCanvasConfig = editCanvasConfig
        chartEditStore.requestGlobalConfig = requestGlobalConfig
        chartEditStore.componentList = componentList
        return storageList[i]
      }
    }
  }
}
