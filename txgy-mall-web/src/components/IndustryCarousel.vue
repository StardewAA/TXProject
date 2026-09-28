<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
const slides = [
  { image: 'protection', title: '让工程材料采购更简单', subtitle: '汇集防腐、保温及施工配套材料，按工况选材，按规格采购', keyword: '防腐', tag: 'INDUSTRIAL MATERIALS PROCUREMENT' },
  { image: 'insulation', title: '保温材料集中选购', subtitle: '覆盖保温、保冷与外护材料，快速匹配管线和设备使用需要', keyword: '保温', tag: 'THERMAL INSULATION' },
  { image: 'pipes', title: '管材管件规格清晰', subtitle: '从材质、口径到连接方式，集中查看工程所需管件与施工配套', keyword: '管', tag: 'ENGINEERING MATERIALS' },
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
      <div class="industry-copy"><span>{{ slide.tag }}</span><h1>{{ slide.title }}</h1><p>{{ slide.subtitle }}</p><div class="slide-points"><b>品类齐全</b><b>企业直供</b><b>采购可追踪</b></div><MallLink :to="{ path: '/goods', query: { keyword: slide.keyword } }">选购{{ slide.keyword === '管' ? '管材' : slide.keyword }}材料 <i>→</i></MallLink></div>
    </article>
    <div class="carousel-controls"><button aria-label="上一张" @click="move(-1)">←</button><button v-for="(slide, index) in slides" :key="slide.image" :aria-label="'切换到第' + (index + 1) + '张'" :aria-pressed="active === index" :class="{ selected: active === index }" @click="active = index">{{ index + 1 }}</button><button aria-label="下一张" @click="move(1)">→</button></div>
  </section>
</template>
<style scoped>
.industry-carousel{position:relative;min-width:0;height:100%;min-height:466px;overflow:hidden;background:#d9e5e5}
.industry-slide{height:100%;position:relative}
.industry-art{position:absolute;width:100%;height:100%;object-fit:cover;object-position:right center}
.industry-slide::after{content:'';position:absolute;inset:0;pointer-events:none;background:linear-gradient(90deg,#eaf4f3f5 0%,#eaf4f3d9 37%,#eaf4f34d 68%,transparent 82%)}
.industry-copy{position:relative;z-index:2;padding:58px 38px;width:76%;color:#143943}
.industry-copy>span{font-size:9px;font-weight:700;letter-spacing:2px;color:#287c70}
.industry-copy h1{max-width:430px;margin:18px 0 13px;font-size:36px;line-height:1.32;letter-spacing:1px}
.industry-copy p{max-width:365px;margin:0;font-size:13px;line-height:1.85;color:#476970}
.slide-points{display:flex;gap:17px;margin-top:22px;color:#244e4d;font-size:10px}.slide-points b{position:relative;padding-left:13px}.slide-points b::before{content:'✓';position:absolute;left:0;color:#0c806e}
.industry-copy a{display:inline-flex;align-items:center;justify-content:space-between;min-width:168px;margin-top:26px;padding:13px 18px;border-radius:5px;background:#0f806d;color:white;font-size:13px;font-weight:700;box-shadow:0 8px 20px #0e625933}.industry-copy a i{font-size:18px;font-style:normal}
.carousel-controls{position:absolute;bottom:20px;left:38px;display:flex;gap:6px;z-index:3}
.carousel-controls button{min-width:26px;height:26px;padding:0 6px;border:1px solid #ffffff94;border-radius:4px;background:#173e47c4;color:white;font-size:10px;cursor:pointer}
.carousel-controls .selected{border-color:#0e806d;background:#fff;color:#10594e}
@media(max-width:1220px){.industry-copy{width:80%;padding:56px 27px}.industry-copy h1{font-size:30px}.slide-points{gap:10px}}
</style>
