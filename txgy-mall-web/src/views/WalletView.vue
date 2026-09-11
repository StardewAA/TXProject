<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { walletApi } from '@/api/mall'
import MemberPage from '@/components/MemberPage.vue'
import type { Wallet, WalletPackage, WalletTransaction } from '@/types/mall'
import { formatDate, formatMoney } from '@/utils/format'

const router = useRouter()
const wallet = ref<Wallet | null>(null)
const packages = ref<WalletPackage[]>([])
const transactions = ref<WalletTransaction[]>([])
const amountYuan = ref(100)
const loading = ref(true)
const creating = ref(false)

async function load() {
  loading.value = true
  try {
    const [walletData, packageData, transactionPage] = await Promise.all([
      walletApi.get(), walletApi.getPackages(), walletApi.getTransactions({ pageNo: 1, pageSize: 20 }),
    ])
    wallet.value = walletData
    packages.value = packageData || []
    transactions.value = transactionPage?.list || []
  } finally { loading.value = false }
}

async function recharge(packageId?: string | number) {
  const payPrice = Math.round(Number(amountYuan.value) * 100)
  if (!packageId && payPrice <= 0) return ElMessage.warning('请输入正确的充值金额')
  creating.value = true
  try {
    const result = await walletApi.createRecharge(packageId ? { packageId } : { payPrice })
    await router.push(`/pay/${result.payOrderId}?redirect=/member/wallet`)
  } finally { creating.value = false }
}

onMounted(load)
</script>

<template>
  <MemberPage title="我的钱包" description="查看账户余额、充值及资金流水">
    <div v-if="loading" class="loading-box"><el-skeleton :rows="8" animated /></div>
    <template v-else>
      <section class="wallet-card">
        <div><span>可用余额</span><strong>¥{{ formatMoney(wallet?.balance) }}</strong></div>
        <div><span>累计充值</span><b>¥{{ formatMoney(wallet?.totalRecharge) }}</b></div>
        <div><span>累计支出</span><b>¥{{ formatMoney(wallet?.totalExpense) }}</b></div>
      </section>
      <section class="recharge-section">
        <h2>余额充值</h2>
        <div v-if="packages.length" class="package-grid">
          <button v-for="item in packages" :key="item.id" :disabled="creating" @click="recharge(item.id)">
            <strong>充值 ¥{{ formatMoney(item.payPrice) }}</strong><span v-if="item.bonusPrice">到账 ¥{{ formatMoney(item.payPrice + item.bonusPrice) }}</span><small>{{ item.name }}</small>
          </button>
        </div>
        <div class="custom-recharge"><span>自定义金额</span><el-input-number v-model="amountYuan" :min="0.01" :precision="2" :step="100" /><span>元</span><el-button type="primary" :loading="creating" @click="recharge()">去充值</el-button></div>
      </section>
      <section class="transaction-section"><h2>余额流水</h2><el-table :data="transactions" empty-text="暂无余额流水"><el-table-column prop="title" label="类型" /><el-table-column label="金额"><template #default="scope"><b :class="Number(scope.row.price) >= 0 ? 'income' : 'expense'">{{ Number(scope.row.price) >= 0 ? '+' : '' }}¥{{ formatMoney(scope.row.price) }}</b></template></el-table-column><el-table-column label="时间"><template #default="scope">{{ formatDate(scope.row.createTime) }}</template></el-table-column></el-table></section>
    </template>
  </MemberPage>
</template>

<style scoped lang="scss">
.wallet-card { display: grid; grid-template-columns: 1.5fr 1fr 1fr; gap: 1px; overflow: hidden; border-radius: 10px; background: #e7eaed; }.wallet-card > div { min-height: 130px; display: flex; flex-direction: column; justify-content: center; padding: 28px; background: linear-gradient(120deg, #fff7f2, #fff); }.wallet-card span { color: var(--muted); }.wallet-card strong { margin-top: 10px; color: var(--brand); font-size: 34px; }.wallet-card b { margin-top: 10px; font-size: 21px; }.recharge-section, .transaction-section { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--line); }.recharge-section h2, .transaction-section h2 { margin: 0 0 18px; font-size: 18px; }.package-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }.package-grid button { min-height: 96px; padding: 16px; border: 1px solid var(--line); border-radius: 7px; background: white; text-align: left; }.package-grid button:hover { border-color: var(--brand); background: #fff8f4; }.package-grid strong, .package-grid span, .package-grid small { display: block; }.package-grid span { margin-top: 6px; color: var(--brand); }.package-grid small { margin-top: 6px; color: var(--muted); }.custom-recharge { display: flex; align-items: center; gap: 12px; margin-top: 16px; padding: 18px; background: #f7f8f9; }.income { color: #1b8b57; }.expense { color: #d84a2d; }
</style>
