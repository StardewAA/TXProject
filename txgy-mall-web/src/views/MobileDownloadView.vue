<script setup lang="ts">
import QRCode from 'qrcode'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { createMobileReleaseConfig, defaultMobileMallUrl, mobileConnectionLinks, normalizeMobileMallUrl } from '@/utils/mobile-release'

const release = ref(createMobileReleaseConfig(import.meta.env, window.location.origin, import.meta.env.PROD))
const qrCode = ref('')
const qrError = ref(false)
const loading = ref(true)
const packageError = ref('')
const installStarted = ref(false)
const pcAddress = ref(new URL(release.value.landingUrl || window.location.origin).origin)
const incomingH5 = new URL(window.location.href).searchParams.get('h5') || ''
const h5Address = ref(normalizeMobileMallUrl(incomingH5) || defaultMobileMallUrl(window.location.origin, import.meta.env.VITE_MALL_H5_URL, import.meta.env.VITE_MALL_H5_PORT))
const connection = computed(() => mobileConnectionLinks(pcAddress.value, h5Address.value))
const controller = new AbortController()
let qrGeneration = 0

watch(connection, async (links) => {
  const generation = ++qrGeneration
  qrCode.value = ''; qrError.value = false
  if (!links) return
  try {
    const image = await QRCode.toDataURL(links.landingUrl, { width: 220, margin: 2, errorCorrectionLevel: 'M' })
    if (generation === qrGeneration) qrCode.value = image
  } catch { if (generation === qrGeneration) qrError.value = true }
}, { immediate: true })

onMounted(async () => {
  document.documentElement.classList.add('mobile-download-page')
  if (release.value.ready) { loading.value = false; return }
  const timeout = window.setTimeout(() => controller.abort(), 10000)
  try {
    const response = await fetch('/downloads/android-release.json', { cache: 'no-store', signal: controller.signal })
    if (!response.ok) throw new Error('未找到版本信息')
    const metadata = await response.json()
    const candidate = createMobileReleaseConfig(import.meta.env, window.location.origin, import.meta.env.PROD, metadata)
    if (!candidate.ready || !candidate.apkUrl) throw new Error('版本信息尚未配置完整')
    // Do not advertise a stale manifest or an SPA HTML fallback as an installable APK.
    const apk = await fetch(candidate.apkUrl, { method: 'HEAD', cache: 'no-store', signal: controller.signal })
    if (!apk.ok || /text\/html/i.test(apk.headers.get('content-type') || '') || Number(apk.headers.get('content-length')) !== metadata.fileSizeBytes) {
      throw new Error('安装包未部署完整，请重新部署 PC 商城')
    }
    release.value = candidate
  } catch {
    packageError.value = '安装包或版本信息暂不可用，请确认 PC 商城已更新部署。'
  } finally {
    window.clearTimeout(timeout)
    loading.value = false
  }
})

onUnmounted(() => { controller.abort(); qrGeneration++; document.documentElement.classList.remove('mobile-download-page') })
</script>

<template>
  <div class="mobile-download">
    <header class="download-header">
      <MallLink to="/" class="download-brand"><span class="download-brand__mark">防</span><span><b>防腐保温商城</b><small>工程材料 · 一站式采购</small></span></MallLink>
      <span class="download-platform">ANDROID APP</span>
    </header>

    <main class="download-content">
      <section class="download-intro">
        <div class="download-intro__copy">
          <span class="download-eyebrow">MOBILE PROCUREMENT</span>
          <h1>工程采购，<br />随时随地</h1>
          <p>在手机上浏览材料、管理购物车和订单，并继续使用商城已开放的移动端服务。</p>
          <div class="download-platform-pill">Android 教学演示版 · cn.txgy.mall</div>
        </div>
        <div class="download-qr-card">
          <img v-if="qrCode" :src="qrCode" alt="移动端下载落地页二维码" />
          <div v-else class="download-qr-placeholder" role="status">{{ qrError ? '二维码暂不可用' : '填写下方局域网地址后显示二维码' }}</div>
          <b>手机扫码打开本页</b>
          <small>下载、安装，返回本页一键进入商城</small>
        </div>
      </section>

      <section class="download-release panel">
        <div class="download-release__heading">
          <div><small>RELEASE INFORMATION</small><h2>版本与安装信息</h2></div>
          <span :class="['release-status', { 'is-ready': release.ready }]">{{ loading ? '检查安装包…' : release.ready ? '可下载' : '暂不可用' }}</span>
        </div>
        <dl class="release-grid">
          <div><dt>平台</dt><dd>Android</dd></div>
          <div><dt>版本</dt><dd>{{ release.version || '待发布' }}</dd></div>
          <div><dt>安装包大小</dt><dd>{{ release.fileSize || '待发布' }}</dd></div>
          <div><dt>更新日期</dt><dd>{{ release.releaseDate || '待发布' }}</dd></div>
          <div><dt>最低系统</dt><dd>{{ release.minimumAndroidVersion || '待确认' }}</dd></div>
          <div><dt>权限说明</dt><dd>{{ release.permissionsSummary || '将随正式版本一并公示' }}</dd></div>
        </dl>

        <a v-if="release.ready && release.apkUrl" class="download-action" :href="release.apkUrl" download @click="installStarted = true">
          第 1 步：下载并安装 Android 包 <span>↓</span>
        </a>
        <button v-else class="download-action is-disabled" type="button" disabled>{{ loading ? '正在检查安装包' : '安装包暂不可用' }}</button>
        <p v-if="packageError" class="download-error" role="status">{{ packageError }}</p>
        <a v-if="connection" class="download-open-app" :class="{ 'download-open-app--ready': installStarted }" :href="connection.appUrl">第 2 步：安装后返回本页，点此进入商城 →</a>

        <div v-if="release.ready" class="release-checksum"><b>SHA-256</b><code>{{ release.sha256 }}</code></div>
        <p class="download-notice">仅支持 Android 6.0 及以上，不支持 iPhone。安装器的“打开”按钮无法把浏览器中的电脑地址交给 App；首次安装后请返回此下载页点击第 2 步，无需手输网址。之后从桌面打开 App 会直接进入上次连接的商城。本包供课程展示使用，需要联网访问已启动的商城。</p>
      </section>

      <section class="download-release panel connection-panel">
        <div class="download-release__heading"><div><small>CONNECT YOUR MALL</small><h2>安装后，连接商城</h2></div></div>
        <p class="download-notice">手机与部署电脑连接同一网络。地址会随二维码传到手机，不包含登录凭据；切换商城需重新登录。</p>
        <label for="pc-address">PC 商城地址</label>
        <input id="pc-address" v-model="pcAddress" type="url" spellcheck="false" placeholder="http://电脑局域网IP:15175" />
        <label for="h5-address">移动商城地址</label>
        <input id="h5-address" v-model="h5Address" type="url" spellcheck="false" placeholder="http://电脑局域网IP:3000" />
        <p v-if="!connection" class="download-error" role="status">请将 localhost / 127.0.0.1 替换为电脑的局域网 IPv4 地址（可在电脑运行 ipconfig 查看），并确认两个端口可从手机访问。</p>
        <div v-else class="connection-actions">
          <a class="download-action" :href="connection.appUrl">已安装？打开并连接 App →</a>
          <a :href="connection.h5Url" target="_blank" rel="noopener noreferrer">先在手机浏览器测试商城能否打开 ↗</a>
        </div>
        <p class="download-notice">电脑上设置好地址后，请用手机相机或浏览器扫码打开本页。若在微信内无法下载或打开 App，请选择“在浏览器中打开”。HTTP 仅用于可信局域网教学演示；公网部署应使用 HTTPS。</p>
      </section>

      <footer class="download-footer">
        <span>安装或使用遇到问题？</span>
        <a v-if="release.feedbackUrl" :href="release.feedbackUrl" target="_blank" rel="noopener noreferrer">联系反馈</a>
        <span v-else>请联系项目部署人员，核对地址、网络和服务状态</span>
        <MallLink to="/">返回商城首页</MallLink>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.mobile-download { min-height: 100vh; background: #f3f6f5; color: #17343c; }
.download-header { display:flex; align-items:center; justify-content:space-between; min-height:76px; padding:12px max(22px,calc((100vw - 1180px)/2)); border-bottom:1px solid #e2e9e6; background:#fff; }
.download-brand { display:flex; align-items:center; gap:12px; }
.download-brand__mark { display:grid; place-items:center; width:42px; height:42px; border-radius:13px; background:#0f806d; color:#fff; font-size:23px; font-weight:800; }
.download-brand b,.download-brand small { display:block; }.download-brand b { font-size:16px; }.download-brand small { margin-top:4px; color:#83908c; font-size:11px; }
.download-platform,.download-eyebrow { color:#0f806d; font-size:10px; font-weight:800; letter-spacing:2px; }
.download-content { width:min(980px,calc(100% - 40px)); margin:30px auto 0; }
.download-intro { display:grid; grid-template-columns:minmax(0,1fr) 280px; gap:32px; align-items:center; padding:34px 42px; border-radius:20px; background:linear-gradient(120deg,#102f3a,#0d7768); color:#fff; box-shadow:0 16px 40px #102f3a1c; }
.download-intro h1 { margin:12px 0; font-size:clamp(30px,5vw,46px); line-height:1.24; letter-spacing:1px; }.download-intro p { max-width:520px; margin:0; color:#c4d9d5; line-height:1.8; font-size:14px; }
.download-platform-pill { display:inline-flex; margin-top:22px; padding:8px 13px; border:1px solid #ffffff55; border-radius:999px; color:#ddf5ed; font-size:12px; }
.download-qr-card { display:flex; flex-direction:column; align-items:center; gap:9px; padding:18px; border-radius:16px; background:#fff; color:#223c42; text-align:center; }.download-qr-card img,.download-qr-placeholder { width:180px; height:180px; border-radius:8px; }.download-qr-placeholder { display:grid; place-items:center; padding:18px; background:#f2f5f4; color:#82908b; font-size:12px; }.download-qr-card b { font-size:14px; }.download-qr-card small { color:#8a9692; font-size:11px; }
.download-release { margin-top:22px; padding:28px 32px; }.download-release__heading { display:flex; justify-content:space-between; align-items:center; gap:16px; }.download-release__heading small { color:#0f806d; font-size:9px; font-weight:800; letter-spacing:2px; }.download-release h2 { margin:6px 0 0; font-size:20px; }.release-status { padding:6px 11px; border-radius:999px; background:#f2f3f3; color:#78847f; font-size:12px; }.release-status.is-ready { background:#e5f5ee; color:#0b795f; }
.release-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:1px; margin:22px 0; overflow:hidden; border:1px solid #e7ece9; border-radius:12px; background:#e7ece9; }.release-grid>div { min-height:76px; padding:13px 15px; background:#fff; }.release-grid dt { color:#87928e; font-size:11px; }.release-grid dd { margin:8px 0 0; color:#243c40; font-size:13px; line-height:1.6; overflow-wrap:anywhere; }
.download-action { display:flex; justify-content:center; align-items:center; gap:14px; width:100%; min-height:50px; border:0; border-radius:10px; background:#0f806d; color:#fff; font-size:15px; font-weight:700; cursor:pointer; }.download-action:hover { background:#0b6d5d; }.download-action.is-disabled { background:#e8eeeb; color:#8a9691; cursor:not-allowed; }
.release-checksum { display:grid; grid-template-columns:80px minmax(0,1fr); gap:10px; align-items:start; margin-top:18px; color:#63736e; font-size:11px; }.release-checksum code { overflow-wrap:anywhere; color:#334640; }
.download-notice { margin:18px 0 0; color:#7b8883; font-size:12px; line-height:1.8; }.download-footer { display:flex; justify-content:center; align-items:center; gap:20px; padding:26px 12px 34px; color:#84918c; font-size:12px; }.download-footer a { color:#0f806d; }
.connection-panel label { display:block; margin:18px 0 8px; font-size:13px; font-weight:600; }.connection-panel input { box-sizing:border-box; width:100%; min-height:46px; padding:10px 14px; border:1px solid #d7e3dd; border-radius:9px; background:#f8fbfa; color:#23443b; font:inherit; font-size:14px; }.connection-panel input:focus { outline:2px solid #0f806d; outline-offset:2px; }.connection-actions { display:flex; flex-direction:column; align-items:center; gap:16px; margin-top:22px; }.connection-actions>a:not(.download-action) { color:#0f806d; font-size:13px; }.download-error { color:#b34b34; font-size:13px; line-height:1.8; }
.download-open-app { display:block; padding:16px 8px 0; text-align:center; color:#0f806d; font-size:14px; font-weight:600; }
.download-open-app--ready { margin-top:12px; padding:14px 12px; border:1px solid #0f806d; border-radius:10px; background:#e5f5ee; font-weight:700; }
@media(max-width:680px) { .download-header { min-height:62px; padding:10px 16px; }.download-content { width:calc(100% - 24px); margin-top:14px; }.download-intro { grid-template-columns:1fr; gap:20px; padding:24px 20px; border-radius:15px; }.download-intro h1 { font-size:32px; }.download-qr-card { display:grid; grid-template-columns:84px 1fr; justify-items:start; text-align:left; padding:12px; }.download-qr-card img,.download-qr-placeholder { grid-row:span 2; width:84px; height:84px; }.download-release { padding:20px 16px; }.release-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.download-footer { flex-wrap:wrap; gap:12px 18px; } }
</style>
