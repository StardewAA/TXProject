import * as THREE from 'three'
import { PROCESS_FLOW_Y } from './layout'
import { createLabelSprite, refreshLabelSprite } from './Labels'

interface ProcessNode {
  processId: number
  group: THREE.Group
  plateMat: THREE.MeshStandardMaterial
  ringMat: THREE.MeshBasicMaterial
  label: THREE.Sprite
  phase: number
}

interface Segment {
  from: THREE.Vector3
  to: THREE.Vector3
  parts: THREE.Mesh[]
  cursors: number[]
  speed: number
}

/**
 * 悬浮工序链。
 *
 * 数据来自 mes/pro/route-process/list-by-route，后端已经把顺序（sort）、
 * 后继关系（nextProcessId）、配色（colorCode）、是否关键工序（keyFlag）、
 * 是否检验点（checkFlag）都给好了，这里只负责画出来并让物料流动起来。
 */
export class ProcessFlow {
  public group: THREE.Group = new THREE.Group()

  private nodes = new Map<number, ProcessNode>()
  private segments: Segment[] = []
  private elapsed = 0
  // 工序链比车间地面高，离相机更近，同样的世界宽度在屏幕上会更大；
  // 所以跨度收窄到与中间两座车间对齐，避免两端工序跑出画幅。
  private spanStart = -48
  private spanEnd = 50

  private nodePosition(index: number, count: number): THREE.Vector3 {
    if (count <= 1) return new THREE.Vector3((this.spanStart + this.spanEnd) / 2, PROCESS_FLOW_Y, 0)
    const step = (this.spanEnd - this.spanStart) / (count - 1)
    return new THREE.Vector3(this.spanStart + step * index, PROCESS_FLOW_Y, 0)
  }

  public rebuild(routeProcess: any[], tasks: any[]) {
    // 清空旧内容
    this.disposeChildren()
    this.nodes.clear()
    this.segments = []

    const items = [...(routeProcess || [])].sort((a, b) => (a.sort || 0) - (b.sort || 0))
    if (!items.length) return

    // 进度按工序聚合生产任务
    const progress = new Map<number, { produced: number; quantity: number }>()
    ;(tasks || []).forEach((task: any) => {
      if (!task || !task.processId) return
      const current = progress.get(task.processId) || { produced: 0, quantity: 0 }
      current.produced += Number(task.producedQuantity) || 0
      current.quantity += Number(task.quantity) || 0
      progress.set(task.processId, current)
    })

    items.forEach((item: any, index: number) => {
      const position = this.nodePosition(index, items.length)
      const node = this.buildNode(item, position, progress.get(item.processId))
      this.nodes.set(item.processId, node)
      this.group.add(node.group)
    })

    // 工序之间的连线与流动的物料
    for (let i = 0; i < items.length - 1; i++) {
      const from = this.nodePosition(i, items.length)
      const to = this.nodePosition(i + 1, items.length)
      this.buildSegment(from, to, items[i])
    }

  }

  private buildNode(item: any, position: THREE.Vector3, progress?: { produced: number; quantity: number }) {
    const group = new THREE.Group()
    group.position.copy(position)

    const color = new THREE.Color(item.colorCode || '#409EFF')
    const colorHex = color.getHex()

    const plateMat = new THREE.MeshStandardMaterial({
      color: colorHex,
      emissive: colorHex,
      emissiveIntensity: 0.45,
      metalness: 0.45,
      roughness: 0.35
    })
    const plate = new THREE.Mesh(new THREE.CylinderGeometry(4.4, 4.4, 0.9, 6), plateMat)
    plate.rotation.y = Math.PI / 6
    group.add(plate)

    // 外圈光环
    const ringMat = new THREE.MeshBasicMaterial({
      color: colorHex,
      transparent: true,
      opacity: 0.5,
      side: THREE.DoubleSide,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
    const ring = new THREE.Mesh(new THREE.RingGeometry(5.3, 5.9, 48), ringMat)
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.2
    group.add(ring)

    // 关键工序：金色外环
    if (item.keyFlag) {
      const keyRing = new THREE.Mesh(
        new THREE.RingGeometry(6.3, 6.6, 48),
        new THREE.MeshBasicMaterial({
          color: 0xffc53d,
          transparent: true,
          opacity: 0.85,
          side: THREE.DoubleSide,
          depthWrite: false
        })
      )
      keyRing.rotation.x = -Math.PI / 2
      keyRing.position.y = 0.22
      group.add(keyRing)
    }

    // 检验点：青色虚线环（用细环近似）
    if (item.checkFlag) {
      const checkRing = new THREE.Mesh(
        new THREE.RingGeometry(7.0, 7.25, 48),
        new THREE.MeshBasicMaterial({
          color: 0x38e8ff,
          transparent: true,
          opacity: 0.8,
          side: THREE.DoubleSide,
          depthWrite: false
        })
      )
      checkRing.rotation.x = -Math.PI / 2
      checkRing.position.y = 0.24
      group.add(checkRing)
    }

    // 托盘到标牌之间的细光柱，比实心方柱干净
    const stem = new THREE.Mesh(
      new THREE.CylinderGeometry(0.09, 0.09, 6.4, 8),
      new THREE.MeshBasicMaterial({
        color: colorHex,
        transparent: true,
        opacity: 0.35,
        depthWrite: false,
        blending: THREE.AdditiveBlending
      })
    )
    stem.position.y = 3.6
    group.add(stem)

    const label = createLabelSprite(
      {
        title: item.processName || '工序',
        subtitle: this.nodeSubtitle(item, progress),
        accent: item.colorCode || '#409EFF',
        fontSize: 38
      },
      4.4
    )
    label.position.set(0, 7.6, 0)
    group.add(label)

    return { processId: item.processId, group, plateMat, ringMat, label, phase: Math.random() * Math.PI * 2 }
  }

  private nodeSubtitle(item: any, progress?: { produced: number; quantity: number }) {
    const parts = [`第 ${item.sort} 道`]
    if (item.keyFlag) parts.push('关键工序')
    if (item.checkFlag) parts.push('检验点')
    if (progress && progress.quantity > 0) {
      const rate = Math.round((progress.produced / progress.quantity) * 100)
      parts.push(`产出 ${rate}%`)
    }
    return parts.join(' · ')
  }

  private buildSegment(from: THREE.Vector3, to: THREE.Vector3, sourceItem: any) {
    const gap = Math.abs(to.x - from.x)
    const midX = (from.x + to.x) / 2
    const color = new THREE.Color(sourceItem.colorCode || '#409EFF').getHex()

    // 双轨输送线
    ;[-0.85, 0.85].forEach((z) => {
      const rail = new THREE.Mesh(
        new THREE.BoxGeometry(gap - 8.4, 0.32, 0.42),
        new THREE.MeshStandardMaterial({
          color: 0x1d4f7d,
          emissive: color,
          emissiveIntensity: 0.35,
          metalness: 0.5,
          roughness: 0.5
        })
      )
      rail.position.set(midX, PROCESS_FLOW_Y - 0.1, z)
      this.group.add(rail)
    })

    // 流动的物料
    const parts: THREE.Mesh[] = []
    const cursors: number[] = []
    const partCount = 3
    for (let i = 0; i < partCount; i++) {
      const part = new THREE.Mesh(
        new THREE.BoxGeometry(1.15, 0.55, 1.15),
        new THREE.MeshBasicMaterial({
          color,
          transparent: true,
          opacity: 0.95,
          blending: THREE.AdditiveBlending,
          depthWrite: false
        })
      )
      part.position.set(from.x, PROCESS_FLOW_Y + 0.5, 0)
      this.group.add(part)
      parts.push(part)
      cursors.push(i / partCount)
    }

    this.segments.push({
      from: new THREE.Vector3(from.x + 4.4, PROCESS_FLOW_Y, 0),
      to: new THREE.Vector3(to.x - 4.4, PROCESS_FLOW_Y, 0),
      parts,
      cursors,
      speed: 0.16
    })
  }

  public update(delta: number) {
    this.elapsed += delta

    this.nodes.forEach((node) => {
      const wave = (Math.sin(this.elapsed * 2.2 + node.phase) + 1) / 2
      node.ringMat.opacity = 0.3 + wave * 0.5
      node.plateMat.emissiveIntensity = 0.35 + wave * 0.4
      node.group.position.y = PROCESS_FLOW_Y + Math.sin(this.elapsed * 1.1 + node.phase) * 0.35
    })

    this.segments.forEach((segment) => {
      segment.cursors = segment.cursors.map((cursor, index) => {
        let next = cursor + delta * segment.speed
        if (next > 1) next -= 1
        const x = segment.from.x + (segment.to.x - segment.from.x) * next
        segment.parts[index].position.x = x
        return next
      })
    })
  }

  private disposeChildren() {
    const remove: THREE.Object3D[] = [...this.group.children]
    remove.forEach((child) => {
      this.group.remove(child)
      child.traverse((obj: any) => {
        if (obj.geometry && obj.geometry.dispose) obj.geometry.dispose()
        if (obj.material) {
          if (Array.isArray(obj.material)) obj.material.forEach((m: any) => m.dispose && m.dispose())
          else obj.material.dispose && obj.material.dispose()
        }
      })
    })
  }

  public dispose() {
    this.disposeChildren()
  }
}
