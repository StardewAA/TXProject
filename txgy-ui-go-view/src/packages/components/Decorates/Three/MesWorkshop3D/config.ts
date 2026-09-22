import { PublicConfigClass } from '@/packages/public'
import { CreateComponentType } from '@/packages/index.d'
import { chartInitConfig } from '@/settings/designSetting'
import { MesWorkshop3DConfig } from './index'
import cloneDeep from 'lodash/cloneDeep'

export const option = {
  dataset: {},
  /** 工艺路线 ID，决定悬浮工序链展示哪条路线 */
  routeId: 1,
  /** 轮询间隔（秒），0 表示只加载一次 */
  pollInterval: 30,
  /** 相机自动环绕 */
  autoRotate: false
}

export default class Config extends PublicConfigClass implements CreateComponentType {
  public key = MesWorkshop3DConfig.key
  public attr = { ...chartInitConfig, w: 1280, h: 760, zIndex: -1 }
  public chartConfig = cloneDeep(MesWorkshop3DConfig)
  public option = cloneDeep(option)
}
