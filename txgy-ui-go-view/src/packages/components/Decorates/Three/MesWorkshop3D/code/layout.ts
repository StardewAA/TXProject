/**
 * 车间三维布局定义。
 *
 * 说明：MES 的车间/工位表里只有「面积」和一段自由文本地址，没有任何坐标或尺寸字段，
 * 因此三维场景里的位置必须在这里显式编排。此文件就是唯一的位置来源。
 * 每个车间的地板尺寸按真实面积做了等比缩放，保证视觉比例可信。
 */

export interface WorkshopSlot {
  id: number
  name: string
  code: string
  area: number
  /** 地板尺寸 [宽(X), 深(Z)]，世界单位 */
  size: [number, number]
  /** 车间中心点 [X, Z] */
  center: [number, number]
  /** 主题色 */
  accent: number
}

export const WORKSHOPS: WorkshopSlot[] = [
  {
    id: 1,
    name: '注塑车间',
    code: 'WS001',
    area: 1200,
    size: [48, 30],
    center: [-46, 0],
    accent: 0x2f86ff
  },
  {
    id: 2,
    name: '组装车间',
    code: 'WS002',
    area: 800,
    size: [40, 26],
    center: [2, 0],
    accent: 0x18d0a4
  },
  {
    id: 3,
    name: '五金车间',
    code: 'WS003',
    area: 600,
    size: [34, 22],
    center: [44, 0],
    accent: 0xffa332
  }
]

/** 工序链悬浮高度 */
export const PROCESS_FLOW_Y = 46

/** 设备在车间内的栅格位置（相对车间中心，已扣掉工位带） */
export const machineSlots = (slot: WorkshopSlot, count: number): Array<[number, number]> => {
  if (count <= 0) return []
  const [w, d] = slot.size
  // 前排留给工位，设备区从 Z 轴 -d/2 起算，后方留出 3 单位边距
  const usableWidth = w - 10
  const usableDepth = d - 14
  const cols = Math.min(count, Math.max(2, Math.ceil(Math.sqrt(count * 1.5))))
  const rows = Math.ceil(count / cols)
  const stepX = cols > 1 ? usableWidth / (cols - 1) : 0
  const stepZ = rows > 1 ? usableDepth / (rows - 1) : 0

  const out: Array<[number, number]> = []
  for (let i = 0; i < count; i++) {
    const row = Math.floor(i / cols)
    const col = i % cols
    const x = cols > 1 ? -usableWidth / 2 + col * stepX : 0
    // 行序从后排往前排（Z 负方向为后排）
    const z = rows > 1 ? -usableDepth / 2 + row * stepZ : 0
    out.push([x, z - 3])
  }
  return out
}

/** 工位沿车间前沿（+Z）一字排开 */
export const workstationSlots = (slot: WorkshopSlot, count: number): Array<[number, number]> => {
  if (count <= 0) return []
  const [w, d] = slot.size
  const usableWidth = w - 12
  const z = d / 2 - 4.5
  if (count === 1) return [[0, z]]
  const step = usableWidth / (count - 1)
  const out: Array<[number, number]> = []
  for (let i = 0; i < count; i++) out.push([-usableWidth / 2 + i * step, z])
  return out
}

/**
 * 设备状态 -> 颜色。
 * 取值来自后端 MesDvMachineryStatusEnum：1 停机 / 2 生产中 / 3 维护中。
 * 停机用低饱和的钢蓝灰表示「没在转」，生产中用高亮绿，维护中用琥珀色。
 */
export const MACHINE_STATUS_COLOR: Record<number, number> = {
  1: 0x5b7891,
  2: 0x22d38a,
  3: 0xf5a623
}

export const MACHINE_STATUS_TEXT: Record<number, string> = {
  1: '停机',
  2: '生产中',
  3: '维护中'
}

export const cssColor = (hex: number) => `#${hex.toString(16).padStart(6, '0')}`
