import * as THREE from 'three'
import {
  WORKSHOPS,
  WorkshopSlot,
  machineSlots,
  workstationSlots,
  MACHINE_STATUS_COLOR,
  MACHINE_STATUS_TEXT,
  cssColor
} from './layout'
import { createLabelSprite, refreshLabelSprite } from './Labels'

export interface FactoryData {
  workshops: any[]
  workstations: any[]
  machinery: any[]
  andon: any[]
}

interface MachineNode {
  id: number
  group: THREE.Group
  bodyMat: THREE.MeshStandardMaterial
  beaconMat: THREE.MeshStandardMaterial
  beacon: THREE.Mesh
  top: THREE.Mesh
  label: THREE.Sprite
  status: number
  phase: number
}

interface StationNode {
  id: number
  group: THREE.Group
  ringMat: THREE.MeshBasicMaterial
  columnMat: THREE.MeshBasicMaterial
  column: THREE.Mesh
  label: THREE.Sprite
  andonCount: number
  phase: number
}

/**
 * 生产车间三维主体：车间地板、工位、设备、安灯告警
 */
export class Factory {
  public group: THREE.Group = new THREE.Group()

  private slots = new Map<number, WorkshopSlot>()
  private hostGroups = new Map<number, THREE.Group>()
  private machines = new Map<number, MachineNode>()
  private stations = new Map<number, StationNode>()
  private elapsed = 0

  constructor() {
    WORKSHOPS.forEach((slot) => {
      this.slots.set(slot.id, slot)
      const host = this.buildWorkshop(slot)
      this.hostGroups.set(slot.id, host)
      this.group.add(host)
    })
  }

  /** 车间本体：地板、顶面着色、边界高亮、转角立柱、车间标牌 */
  private buildWorkshop(slot: WorkshopSlot) {
    const [w, d] = slot.size
    const g = new THREE.Group()
    g.position.set(slot.center[0], 0, slot.center[1])

    const slab = new THREE.Mesh(
      new THREE.BoxGeometry(w, 1.6, d),
      new THREE.MeshStandardMaterial({ color: 0x0b2340, metalness: 0.15, roughness: 0.85 })
    )
    slab.position.y = -0.8
    g.add(slab)

    const surface = new THREE.Mesh(
      new THREE.PlaneGeometry(w - 0.6, d - 0.6),
      new THREE.MeshBasicMaterial({
        color: slot.accent,
        transparent: true,
        opacity: 0.1,
        depthWrite: false
      })
    )
    surface.rotation.x = -Math.PI / 2
    surface.position.y = 0.02
    g.add(surface)

    const edges = new THREE.LineSegments(
      new THREE.EdgesGeometry(new THREE.BoxGeometry(w, 1.6, d)),
      new THREE.LineBasicMaterial({ color: slot.accent, transparent: true, opacity: 0.9 })
    )
    edges.position.y = -0.8
    g.add(edges)

    const postMat = new THREE.MeshStandardMaterial({
      color: slot.accent,
      emissive: slot.accent,
      emissiveIntensity: 0.7,
      metalness: 0.3,
      roughness: 0.4
    })
    const postGeo = new THREE.BoxGeometry(0.5, 7, 0.5)
    const corners: Array<[number, number]> = [
      [-w / 2 + 0.6, -d / 2 + 0.6],
      [w / 2 - 0.6, -d / 2 + 0.6],
      [-w / 2 + 0.6, d / 2 - 0.6],
      [w / 2 - 0.6, d / 2 - 0.6]
    ]
    corners.forEach(([x, z]) => {
      const post = new THREE.Mesh(postGeo, postMat)
      post.position.set(x, 3.5, z)
      g.add(post)
    })

    const label = createLabelSprite(
      {
        title: slot.name,
        subtitle: `${slot.code} · ${slot.area} ㎡`,
        accent: cssColor(slot.accent),
        fontSize: 42
      },
      6.4
    )
    label.position.set(0, 13.5, -d / 2 - 2)
    g.add(label)

    return g
  }

  /** 车间内工位之间的物流走线 */
  private buildConveyor(slot: WorkshopSlot, positions: Array<[number, number]>) {
    const g = new THREE.Group()
    if (positions.length < 2) return g
    const sorted = [...positions].sort((a, b) => a[0] - b[0])
    const start = sorted[0]
    const end = sorted[sorted.length - 1]
    const length = Math.abs(end[0] - start[0])
    if (length < 1) return g

    const bar = new THREE.Mesh(
      new THREE.BoxGeometry(length, 0.3, 1.1),
      new THREE.MeshStandardMaterial({
        color: 0x1d4f7d,
        emissive: slot.accent,
        emissiveIntensity: 0.28,
        metalness: 0.5,
        roughness: 0.5
      })
    )
    bar.position.set((start[0] + end[0]) / 2, 0.55, start[1])
    g.add(bar)
    return g
  }

  /** 应用一批 MES 数据：新增/更新工位与设备 */
  public applyData(data: FactoryData) {
    const andonByStation = new Map<number, number>()
    ;(data.andon || []).forEach((record: any) => {
      // MesProAndonStatusEnum：0 未处置、1 已处置
      if (record && record.status === 0 && record.workstationId) {
        andonByStation.set(record.workstationId, (andonByStation.get(record.workstationId) || 0) + 1)
      }
    })

    this.syncStations(data.workstations || [], andonByStation)
    this.syncMachines(data.machinery || [])
  }

  private syncStations(list: any[], andonByStation: Map<number, number>) {
    const byWorkshop = this.groupByWorkshop(list)

    byWorkshop.forEach((items, workshopId) => {
      const slot = this.slots.get(workshopId)!
      const host = this.hostGroups.get(workshopId)!
      items.sort((a, b) => (a.id || 0) - (b.id || 0))
      const positions = workstationSlots(slot, items.length)

      items.forEach((item, index) => {
        const local = positions[index]
        const andonCount = andonByStation.get(item.id) || 0

        let node = this.stations.get(item.id)
        if (!node) {
          if (!this.conveyorBuilt.has(workshopId)) {
            this.conveyorBuilt.add(workshopId)
            host.add(this.buildConveyor(slot, positions))
          }
          node = this.createStation(item, slot, local, index)
          this.stations.set(item.id, node)
          host.add(node.group)
        }
        node.andonCount = andonCount

        refreshLabelSprite(node.label, {
          title: (item.name || '').replace('验收报工-', ''),
          subtitle:
            andonCount > 0 ? `安灯 ${andonCount} 项待处置` : item.processName || '未绑定工序',
          accent: andonCount > 0 ? '#ff4d5e' : cssColor(slot.accent),
          subtitleColor: andonCount > 0 ? '#ffb3bb' : undefined,
          fontSize: 38
        })
      })
    })
  }

  private conveyorBuilt = new Set<number>()

  private groupByWorkshop(list: any[]) {
    const byWorkshop = new Map<number, any[]>()
    list.forEach((item) => {
      if (!this.slots.has(item.workshopId)) return
      if (!byWorkshop.has(item.workshopId)) byWorkshop.set(item.workshopId, [])
      byWorkshop.get(item.workshopId)!.push(item)
    })
    return byWorkshop
  }

  private createStation(item: any, slot: WorkshopSlot, local: [number, number], index = 0): StationNode {
    const group = new THREE.Group()
    group.position.set(local[0], 0, local[1])

    const ringMat = new THREE.MeshBasicMaterial({
      color: slot.accent,
      transparent: true,
      opacity: 0.55,
      side: THREE.DoubleSide
    })
    const ring = new THREE.Mesh(new THREE.RingGeometry(2.3, 2.75, 48), ringMat)
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.08
    group.add(ring)

    const base = new THREE.Mesh(
      new THREE.CylinderGeometry(1.7, 1.9, 0.5, 28),
      new THREE.MeshStandardMaterial({ color: 0x14304f, metalness: 0.4, roughness: 0.6 })
    )
    base.position.y = 0.25
    group.add(base)

    const columnMat = new THREE.MeshBasicMaterial({
      color: slot.accent,
      transparent: true,
      opacity: 0.32,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
    const column = new THREE.Mesh(new THREE.CylinderGeometry(0.26, 0.26, 8, 14), columnMat)
    column.position.y = 4.6
    group.add(column)

    const label = createLabelSprite(
      {
        title: item.name,
        subtitle: item.processName || '未绑定工序',
        accent: cssColor(slot.accent),
        fontSize: 38
      },
      3
    )
    // 相邻工位标牌上下错开，窄车间里三个工位挤在一起时不会互相压住
    label.position.set(0, index % 2 === 0 ? 10.4 : 13.6, 0)
    group.add(label)

    return {
      id: item.id,
      group,
      ringMat,
      columnMat,
      column,
      label,
      andonCount: 0,
      phase: Math.random() * Math.PI * 2
    }
  }

  private syncMachines(list: any[]) {
    const byWorkshop = this.groupByWorkshop(list)

    byWorkshop.forEach((items, workshopId) => {
      const slot = this.slots.get(workshopId)!
      const host = this.hostGroups.get(workshopId)!
      items.sort((a, b) => (a.id || 0) - (b.id || 0))
      const positions = machineSlots(slot, items.length)

      items.forEach((item, index) => {
        const local = positions[index]
        let node = this.machines.get(item.id)
        if (!node) {
          node = this.createMachine(item, slot, local)
          this.machines.set(item.id, node)
          host.add(node.group)
        }

        const status = Number(item.status) || 1
        node.status = status
        const color = MACHINE_STATUS_COLOR[status] || 0x8aa0b8
        node.bodyMat.color.setHex(color)
        node.bodyMat.emissive.setHex(color)
        node.bodyMat.emissiveIntensity = status === 1 ? 0.24 : status === 2 ? 0.32 : 0.38
        node.beaconMat.color.setHex(color)
        node.beaconMat.emissive.setHex(color)

        refreshLabelSprite(node.label, {
          title: item.name,
          subtitle: `${item.machineryTypeName || '设备'} · ${MACHINE_STATUS_TEXT[status] || '未知'}`,
          accent: cssColor(color),
          fontSize: 36
        })
      })
    })
  }

  private createMachine(item: any, slot: WorkshopSlot, local: [number, number]): MachineNode {
    const group = new THREE.Group()
    group.position.set(local[0], 0, local[1])

    const base = new THREE.Mesh(
      new THREE.BoxGeometry(5.6, 0.5, 4.4),
      new THREE.MeshStandardMaterial({ color: 0x12293f, metalness: 0.45, roughness: 0.6 })
    )
    base.position.y = 0.25
    group.add(base)

    const bodyMat = new THREE.MeshStandardMaterial({
      color: 0x22d38a,
      emissive: 0x22d38a,
      emissiveIntensity: 0.24,
      metalness: 0.35,
      roughness: 0.42
    })
    const body = new THREE.Mesh(new THREE.BoxGeometry(4.6, 2.9, 3.4), bodyMat)
    body.position.y = 1.95
    group.add(body)

    const top = new THREE.Mesh(
      new THREE.BoxGeometry(3.1, 0.95, 2.4),
      new THREE.MeshStandardMaterial({ color: 0x1b3d5e, metalness: 0.5, roughness: 0.5 })
    )
    top.position.y = 3.88
    group.add(top)

    const beaconMat = new THREE.MeshStandardMaterial({
      color: 0x22d38a,
      emissive: 0x22d38a,
      emissiveIntensity: 1.6,
      transparent: true,
      opacity: 0.95
    })
    const beacon = new THREE.Mesh(new THREE.SphereGeometry(0.44, 18, 14), beaconMat)
    beacon.position.y = 4.75
    group.add(beacon)

    const label = createLabelSprite(
      {
        title: item.name,
        subtitle: item.machineryTypeName || '设备',
        accent: cssColor(slot.accent),
        fontSize: 36
      },
      3.2
    )
    label.position.set(0, 7.2, 0)
    group.add(label)

    return {
      id: item.id,
      group,
      bodyMat,
      beaconMat,
      beacon,
      top,
      label,
      status: 1,
      phase: Math.random() * Math.PI * 2
    }
  }

  /** 每帧动画：运行设备呼吸、停机爆闪、安灯闪烁、工位光柱脉动 */
  public update(delta: number) {
    this.elapsed += delta

    // 状态语义见 MesDvMachineryStatusEnum：1 停机 / 2 生产中 / 3 维护中
    this.machines.forEach((node) => {
      const wave = Math.sin(this.elapsed * 2.6 + node.phase)
      if (node.status === 2) {
        // 生产中：呼吸灯 + 机身轻微起伏
        node.beaconMat.emissiveIntensity = 1.3 + wave * 0.7
        node.top.position.y = 3.88 + Math.sin(this.elapsed * 1.8 + node.phase) * 0.08
      } else if (node.status === 3) {
        // 维护中：慢速明暗呼吸
        node.beaconMat.emissiveIntensity = 0.6 + Math.abs(wave) * 0.8
        node.top.position.y = 3.88
      } else {
        // 停机：指示灯几乎不亮
        node.beaconMat.emissiveIntensity = 0.12
        node.top.position.y = 3.88
      }
    })

    this.stations.forEach((node) => {
      const wave = (Math.sin(this.elapsed * 3.2 + node.phase) + 1) / 2
      if (node.andonCount > 0) {
        node.ringMat.color.setHex(0xff4d5e)
        node.columnMat.color.setHex(0xff4d5e)
        node.ringMat.opacity = 0.35 + wave * 0.6
        node.columnMat.opacity = 0.2 + wave * 0.5
        node.column.scale.setScalar(1 + wave * 0.35)
      } else {
        node.ringMat.opacity = 0.35 + wave * 0.25
        node.columnMat.opacity = 0.24 + wave * 0.14
        node.column.scale.setScalar(1)
      }
    })
  }

  /**
   * 标牌分级显示：相机拉远时设备和工位的名字会互相压住，按距离隐藏一层。
   * 车间标牌始终显示，设备的颜色本身已经表达了状态。
   */
  public setLabelLOD(distance: number) {
    const showMachine = distance < 95
    const showStation = distance < 185
    this.machines.forEach((node) => {
      node.label.visible = showMachine
    })
    this.stations.forEach((node) => {
      node.label.visible = showStation
    })
  }
}
