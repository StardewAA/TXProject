import { ConfigType, PackagesCategoryEnum, ChartFrameEnum } from '@/packages/index.d'
import { ChatCategoryEnum, ChatCategoryEnumName } from '../../index.d'

export const MesWorkshop3DConfig: ConfigType = {
  key: 'MesWorkshop3D',
  chartKey: 'VMesWorkshop3D',
  conKey: 'VCMesWorkshop3D',
  title: '车间数字孪生',
  category: ChatCategoryEnum.THREE,
  categoryName: ChatCategoryEnumName.THREE,
  package: PackagesCategoryEnum.DECORATES,
  chartFrame: ChartFrameEnum.COMMON,
  image: 'mesWorkshop3d.png'
}