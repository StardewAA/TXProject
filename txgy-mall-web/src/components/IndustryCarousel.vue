<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
const slides = [
  { image: 'protection', title: '为工程筑起防腐屏障', subtitle: '从底漆到面漆，按基材与工况选择涂层配套', keyword: '防腐', tag: 'CORROSION PROTECTION' },
  { image: 'insulation', title: '让每一层保温更有价值', subtitle: '保温、保冷与外护材料，满足管线与设备选材需要', keyword: '保温', tag: 'THERMAL INSULATION' },
  { image: 'pipes', title: '连接工程所需的每一环', subtitle: '管材管件与施工配套，规格清晰，采购更省心', keyword: '管', tag: 'ENGINEERING MATERIALS' },
]
const active = ref(0)
const paused = ref(false)
const reduced = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
function move(step: number) { active.value = (active.value + step + slides.length) % slides.length }
onMounted(() => {
  reduced.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  timer = setInterval(() => { if (!paused.value && !reduced.value && !document.hidden) move(1) }, 5500)
})
onUnmounted(() => clearInterval(timer))
</script>
<template>
  <section class="industry-carousel" aria-label="工程材料主题轮播" aria-roledescription="轮播" @mouseenter="paused = true" @mouseleave="paused = false" @focusin="paused = true" @focusout="paused = false">
    <article v-for="(slide, index) in slides" v-show="active === index" :key="slide.image" class="industry-slide">
      <img :src="['/banners/industry-hero-v2.png', '/banners/industry-insulation-v2.png', '/banners/industry-pipes-v2.png'][index]" alt="" class="industry-art" :fetchpriority="index === 0 ? 'high' : 'auto'" />
      <div class="industry-copy"><span>{{ slide.tag }}</span><h1>{{ slide.title }}</h1><p>{{ slide.subtitle }}</p><MallLink :to="{ path: '/goods', query: { keyword: slide.keyword } }">选购{{ slide.keyword === '管' ? '管材' : slide.keyword }}材料 ↗</MallLink></div>
    </article>
    <div class="carousel-controls"><button aria-label="上一张" @click="move(-1)">←</button><button v-for="(slide, index) in slides" :key="slide.image" :aria-label="'切换到第' + (index + 1) + '张'" :aria-pressed="active === index" :class="{ selected: active === index }" @click="active = index">{{ index + 1 }}</button><button aria-label="下一张" @click="move(1)">→</button></div>
  </section>
</template>
<style scoped>
.industry-carousel{position:relative;min-width:0;height:100%;min-height:500px;overflow:hidden;background:#d9e5e5}
.industry-slide{height:100%;position:relative}
.industry-art{position:absolute;width:100%;height:100%;object-fit:cover;object-position:right center}
.industry-slide::after{content:'';position:absolute;inset:0;pointer-events:none;background:linear-gradient(90deg,#e1eff3eb 0%,#e1eff3a3 35%,transparent 75%)}
.industry-copy{position:relative;z-index:2;padding:70px 44px;width:70%;color:#153d49}
.industry-copy span{font-size:10px;letter-spacing:2px;color:#39766e}
.industry-copy h1{font-size:38px;line-height:1.45;margin:20px 0;max-width:370px}
.industry-copy p{font-size:14px;line-height:1.9;color:#476770;max-width:310px}
.industry-copy a{display:inline-block;margin-top:24px;padding:13px 22px;background:#167664;color:white;border-radius:4px;font-weight:700;font-size:14px}
.carousel-controls{position:absolute;bottom:24px;left:44px;display:flex;gap:6px;z-index:3}
.carousel-controls button{border:1px solid #ffffff80;background:#234d53b3;color:white;border-radius:3px;min-width:28px;height:28px;padding:0 6px;font-size:11px;cursor:pointer}
.carousel-controls .selected{background:#fff;color:#10594e}
@media(max-width:1180px){.industry-copy{width:78%;padding:70px 30px}.industry-copy h1{font-size:32px}}
</style>
