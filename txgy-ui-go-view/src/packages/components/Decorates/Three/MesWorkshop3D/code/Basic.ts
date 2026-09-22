import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls'

/**
 * 三维场景基础层：场景、相机、渲染器、控制器、灯光、地面
 */
export class Basic {
  public scene: THREE.Scene
  public camera: THREE.PerspectiveCamera
  public renderer: THREE.WebGLRenderer
  public controls: OrbitControls
  public dom: HTMLElement

  constructor(dom: HTMLElement, width: number, height: number) {
    this.dom = dom

    this.scene = new THREE.Scene()
    this.scene.fog = new THREE.Fog(0x061a38, 240, 620)

    this.camera = new THREE.PerspectiveCamera(42, width / height, 0.5, 6000)
    this.camera.position.set(0, 88, 94)

    this.renderer = new THREE.WebGLRenderer({
      alpha: true,
      antialias: true,
      preserveDrawingBuffer: true
    })
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    this.renderer.setSize(width, height)
    this.renderer.setClearAlpha(0)
    this.renderer.domElement.style.display = 'block'
    this.dom.appendChild(this.renderer.domElement)

    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.dampingFactor = 0.07
    // 默认不自动环绕：车间是横向排布的，转起来会让两端跑出画幅；
    // 场景本身有呼吸灯、流向动画和光柱脉动，已经足够「活」。
    this.controls.autoRotate = false
    this.controls.autoRotateSpeed = 0.3
    this.controls.enablePan = false
    this.controls.enableZoom = true
    this.controls.minDistance = 70
    this.controls.maxDistance = 360
    this.controls.minPolarAngle = 0.28
    this.controls.maxPolarAngle = Math.PI / 2.32
    // 目标点取在地板(y=0)与悬浮工序链之间，两块内容才能同框
    this.controls.target.set(0, 20, 0)

    this.addLights()
    this.addGround()
  }

  private addLights() {
    this.scene.add(new THREE.HemisphereLight(0x9fc6ff, 0x0a1b33, 1.15))

    const key = new THREE.DirectionalLight(0xffffff, 1.05)
    key.position.set(78, 140, 96)
    this.scene.add(key)

    const fill = new THREE.DirectionalLight(0x4d8cff, 0.55)
    fill.position.set(-96, 70, -88)
    this.scene.add(fill)

    const rim = new THREE.PointLight(0x35d6ff, 0.75, 420, 2)
    rim.position.set(0, 96, -120)
    this.scene.add(rim)
  }

  private addGround() {
    // 大地面，压住视觉重心
    const plane = new THREE.Mesh(
      new THREE.CircleGeometry(300, 72),
      new THREE.MeshBasicMaterial({ color: 0x07203d, transparent: true, opacity: 0.55 })
    )
    plane.rotation.x = -Math.PI / 2
    plane.position.y = -2.6
    this.scene.add(plane)

    // 淡淡的地面网格，提供纵深感
    const grid = new THREE.GridHelper(620, 62, 0x1d6fd0, 0x123f73)
    ;(grid.material as THREE.Material).transparent = true
    ;(grid.material as THREE.Material).opacity = 0.26
    grid.position.y = -2.5
    this.scene.add(grid)
  }

  public updateSize(width: number, height: number) {
    if (!width || !height) return
    this.renderer.setSize(width, height)
    this.camera.aspect = width / height
    this.camera.updateProjectionMatrix()
  }

  public render() {
    this.renderer.render(this.scene, this.camera)
    this.controls.update()
  }

  public dispose() {
    this.controls && this.controls.dispose()
    this.scene.traverse((obj: any) => {
      if (obj.geometry && obj.geometry.dispose) obj.geometry.dispose()
      if (Array.isArray(obj.material)) {
        obj.material.forEach((m: any) => m.dispose && m.dispose())
      } else if (obj.material && obj.material.dispose) {
        obj.material.dispose()
      }
    })
    this.renderer && this.renderer.dispose()
    const el = this.renderer && this.renderer.domElement
    if (el && el.parentNode) el.parentNode.removeChild(el)
  }
}
