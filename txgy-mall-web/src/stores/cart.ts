import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import {
  platformCartApi,
  type PlatformCart,
  type PlatformCartItem,
  type PlatformCartTenantGroup,
} from '@/api/platform-commerce'
import { StaleSessionError } from '@/api/http'
import { getTenantContext, isCurrentTenantEpoch } from '@/utils/tenant-context'

export const useCartStore = defineStore('mall-cart', () => {
  const tenantGroups = ref<PlatformCartTenantGroup[]>([])
  const loading = ref(false)
  const ready = ref(false)
  const error = ref('')
  const mutating = ref(false)
  let requestId = 0
  let contextEpoch = getTenantContext().epoch
  const allItems = computed(() => tenantGroups.value.flatMap(tenant =>
    tenant.shops.flatMap(shop => shop.items)))
  const items = computed(() => allItems.value.filter(item => item.valid))
  const invalidItems = computed(() => allItems.value.filter(item => !item.valid))
  const count = computed(() => allItems.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0))
  const selectedItems = computed(() => items.value.filter((item) => item.selected))
  const selectedAmount = computed(() =>
    selectedItems.value.reduce((sum, item) => sum + Number(item.price || 0) * Number(item.quantity || 0), 0),
  )

  async function fetchCart(showLoading = true) {
    const epoch = getTenantContext().epoch
    const currentRequest = ++requestId
    const current = () => isCurrentTenantEpoch(epoch) && currentRequest === requestId
    ready.value = false
    error.value = ''
    if (showLoading) loading.value = true
    try {
      const data: PlatformCart = await platformCartApi.getList()
      if (!current()) return
      tenantGroups.value = Array.isArray(data?.tenantGroups) ? data.tenantGroups : []
      ready.value = true
    } catch {
      if (current()) {
        tenantGroups.value = []
        error.value = '购物车暂时加载失败，请重试；未删除任何商品'
      }
    } finally {
      if (current()) loading.value = false
    }
  }

  async function mutate(action: () => Promise<unknown>) {
    if (mutating.value) throw new Error('购物车正在更新，请稍后重试')
    const epoch = getTenantContext().epoch
    mutating.value = true
    try {
      await action()
      if (!isCurrentTenantEpoch(epoch)) throw new StaleSessionError()
      await fetchCart(false)
      if (!isCurrentTenantEpoch(epoch)) throw new StaleSessionError()
    } finally {
      if (isCurrentTenantEpoch(epoch)) mutating.value = false
    }
  }

  async function add(skuId: string | number, quantity: number) {
    let id: string | number | undefined
    await mutate(async () => { id = await platformCartApi.add(skuId, quantity) })
    return id
  }

  function requireValid(item: PlatformCartItem) {
    if (!ready.value || !items.value.some(value => String(value.id) === String(item.id)))
      throw new Error('商品状态已变化，请刷新购物车后重试')
  }

  async function changeCount(item: PlatformCartItem, quantity: number) {
    requireValid(item)
    if (!Number.isSafeInteger(quantity) || quantity < 1) throw new Error('请输入有效采购数量')
    await mutate(() => platformCartApi.updateCount(item.id, quantity))
  }

  async function select(item: PlatformCartItem, selected: boolean) {
    requireValid(item)
    await mutate(() => platformCartApi.updateSelected([item.id], selected))
  }

  async function selectAll(selected: boolean) {
    if (!ready.value || !items.value.length) return
    await mutate(() => platformCartApi.updateSelected(items.value.map((item) => item.id), selected))
  }

  async function remove(ids: Array<string | number>) {
    if (!ids.length) return
    const known = new Set(allItems.value.map(item => String(item.id)))
    if (!ready.value || ids.some(id => !known.has(String(id)))) throw new Error('购物车已更新，请刷新后重试')
    await mutate(() => platformCartApi.delete(ids))
  }

  function reset() {
    requestId += 1
    tenantGroups.value = []
    loading.value = false
    ready.value = false
    error.value = ''
    mutating.value = false
  }
  window.addEventListener('mall:tenant-context-updated', () => {
    if (contextEpoch !== getTenantContext().epoch) { contextEpoch = getTenantContext().epoch; reset() }
  })

  return {
    tenantGroups,
    allItems,
    items,
    invalidItems,
    loading,
    ready,
    error,
    mutating,
    count,
    selectedItems,
    selectedAmount,
    fetchCart,
    add,
    changeCount,
    select,
    selectAll,
    remove,
    reset,
  }
})
