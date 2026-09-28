import * as THREE from 'three'
import { Basic } from './Basic'
import { Factory } from './Factory'
import { ProcessFlow } from './ProcessFlow'
import { fetchMesSnapshot, MesSnapshot } from './DataSource'

export interface WorldOptions {
  dom: HTMLElement
  width: number
  height: number
  routeId?: number
  /** 轮询间隔（毫秒），0 表示只取一次 */
  pollInterval?: number
  /** 相机是否自动环绕 */
  autoRotate?: boolean
}

/**
 * 车间数字孪生场景编排：负责装配三维场景、拉取 MES 数据、驱动渲染循环
 */
export class World {
  public basic: Basic
  public factory: Factory
  public flow: ProcessFlow
  public group: THREE.Group = new THREE.Group()

  private options: WorldOptions
  private clock: THREE.Clock = new THREE.Clock()
  private rafId = 0
  private timer: any = 0
  private disposed = false
  private snapshot: MesSnapshot | null = null
  private onSnapshot?: (snapshot: MesSnapshot) => void

  constructor(options: WorldOptions, onSnapshot?: (snapshot: MesSnapshot) => void) {
    this.options = options
    this.onSnapshot = onSnapshot

    this.basic = new Basic(options.dom, options.width, options.height)
    this.basic.controls.autoRotate = options.autoRotate === true
    this.factory = new Factory()
    this.flow = new ProcessFlow()

    this.group.add(this.factory.group)
    this.group.add(this.flow.group)
    this.basic.scene.add(this.group)

    this.loadData()
    this.render()

    const interval = options.pollInterval === undefined ? 30000 : options.pollInterval
    if (interval > 0) {
      this.timer = setInterval(() => this.loadData(), interval)
    }
  }

  /** 拉取一次 MES 数据并刷新场景 */
  public async loadData() {
    if (this.disposed) return
    try {
      const snapshot = await fetchMesSnapshot(this.options.routeId || 1)
      if (this.disposed) return
      this.snapshot = snapshot

      // 用数据库里的车间名称/面积校正布局标牌（布局文件是几何来源，业务文案以库为准）
      this.factory.applyData({
        workshops: snapshot.workshops,
        workstations: snapshot.workstations,
        machinery: snapshot.machinery,
        andon: snapshot.andon
      })
      this.flow.rebuild(snapshot.routeProcess, snapshot.tasks)

      if (this.onSnapshot) this.onSnapshot(snapshot)
    } catch (error) {
      console.warn('[MesWorkshop3D] 数据加载失败', error)
    }
  }

  private render = () => {
    if (this.disposed) return
    this.rafId = requestAnimationFrame(this.render)

    const delta = Math.min(this.clock.getDelta(), 0.1)
    if (document.hidden) return

    this.factory.update(delta)
    this.flow.update(delta)
    this.factory.setLabelLOD(this.basic.camera.position.distanceTo(this.basic.controls.target))
    this.basic.render()
  }

  public updateSize(width: number, height: number) {
    this.basic.updateSize(width, height)
  }

  public getSnapshot() {
    return this.snapshot
  }

  public dispose() {
    this.disposed = true
    cancelAnimationFrame(this.rafId)
    if (this.timer) clearInterval(this.timer)
    this.flow.dispose()
    this.basic.dispose()
  }
}
