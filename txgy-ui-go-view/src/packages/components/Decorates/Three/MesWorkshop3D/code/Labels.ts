import * as THREE from 'three'

const FONT_STACK = '"Microsoft YaHei","PingFang SC","Noto Sans SC","Hiragino Sans GB",sans-serif'

export interface LabelOptions {
  title: string
  subtitle?: string
  accent?: string
  titleColor?: string
  subtitleColor?: string
  fontSize?: number
  align?: 'center' | 'left'
  background?: string
}

const roundRect = (
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number
) => {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

/**
 * 用 Canvas 画一张带底板的文字贴图，供三维场景做标牌使用
 */
export const createTextTexture = (opts: LabelOptions) => {
  const fontSize = opts.fontSize || 40
  const subSize = Math.round(fontSize * 0.62)
  const padX = Math.round(fontSize * 0.62)
  const padY = Math.round(fontSize * 0.4)
  const accent = opts.accent || '#38bdf8'
  const titleColor = opts.titleColor || '#eaf6ff'
  const subtitleColor = opts.subtitleColor || accent
  const scale = 2

  const measure = document.createElement('canvas').getContext('2d')!
  measure.font = `700 ${fontSize}px ${FONT_STACK}`
  const titleWidth = measure.measureText(opts.title || '').width
  let subWidth = 0
  if (opts.subtitle) {
    measure.font = `500 ${subSize}px ${FONT_STACK}`
    subWidth = measure.measureText(opts.subtitle).width
  }

  const contentWidth = Math.max(titleWidth, subWidth, fontSize * 1.6)
  const width = Math.ceil(contentWidth + padX * 2)
  const height = Math.ceil(fontSize + (opts.subtitle ? subSize + fontSize * 0.22 : 0) + padY * 2)

  const canvas = document.createElement('canvas')
  canvas.width = width * scale
  canvas.height = height * scale
  const ctx = canvas.getContext('2d')!
  ctx.scale(scale, scale)

  // 底板
  roundRect(ctx, 1, 1, width - 2, height - 2, fontSize * 0.3)
  ctx.fillStyle = opts.background || 'rgba(6,22,44,0.78)'
  ctx.fill()
  ctx.lineWidth = 2
  ctx.strokeStyle = accent
  ctx.globalAlpha = 0.85
  ctx.stroke()
  ctx.globalAlpha = 1

  // 左侧强调条
  ctx.fillStyle = accent
  roundRect(ctx, 6, height * 0.24, 5, height * 0.52, 2.5)
  ctx.fill()

  const centerX = width / 2
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'

  let cursorY = padY + fontSize * 0.52
  ctx.font = `700 ${fontSize}px ${FONT_STACK}`
  ctx.fillStyle = titleColor
  ctx.fillText(opts.title || '', centerX, cursorY)

  if (opts.subtitle) {
    cursorY += fontSize * 0.66
    ctx.font = `500 ${subSize}px ${FONT_STACK}`
    ctx.fillStyle = subtitleColor
    ctx.fillText(opts.subtitle, centerX, cursorY)
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.minFilter = THREE.LinearFilter
  texture.magFilter = THREE.LinearFilter
  texture.needsUpdate = true
  return { texture, aspect: width / height }
}

/**
 * 生成一个面向相机的标牌精灵，worldHeight 控制在世界坐标里的高度
 */
export const createLabelSprite = (opts: LabelOptions, worldHeight: number) => {
  const { texture, aspect } = createTextTexture(opts)
  const material = new THREE.SpriteMaterial({
    map: texture,
    transparent: true,
    depthTest: false,
    depthWrite: false
  })
  const sprite = new THREE.Sprite(material)
  sprite.scale.set(worldHeight * aspect, worldHeight, 1)
  sprite.renderOrder = 20
  ;(sprite as any).userData.labelAspect = aspect
  ;(sprite as any).userData.worldHeight = worldHeight
  return sprite
}

/**
 * 更换标牌内容（重建贴图并释放旧贴图）
 */
export const refreshLabelSprite = (sprite: THREE.Sprite, opts: LabelOptions) => {
  const material = sprite.material as THREE.SpriteMaterial
  const worldHeight = (sprite as any).userData.worldHeight || sprite.scale.y
  if (material.map) material.map.dispose()
  const { texture, aspect } = createTextTexture(opts)
  material.map = texture
  material.needsUpdate = true
  sprite.scale.set(worldHeight * aspect, worldHeight, 1)
  ;(sprite as any).userData.labelAspect = aspect
}