import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { cartApi } from '@/api/mall'
import type { CartItem } from '@/types/mall'

export const useCartStore = defineStore('mall-cart', () => {
  const items = ref<CartItem[]>([])
  const invalidItems = ref<CartItem[]>([])
  const loading = ref(false)
  const count = computed(() => items.value.reduce((sum, item) => sum + Number(item.count || 0), 0))
  const selectedItems = computed(() => items.value.filter((item) => item.selected))
  const selectedAmount = computed(() =>
    selectedItems.value.reduce((sum, item) => sum + Number(item.sku?.price || 0) * Number(item.count || 0), 0),
  )

  async function fetchCart(showLoading = true) {
    if (showLoading) loading.value = true
    try {
      const data = await cartApi.getList()
      items.value = Array.isArray(data?.validList) ? data.validList : []
      invalidItems.value = Array.isArray(data?.invalidList) ? data.invalidList : []
    } catch {
      items.value = []
      invalidItems.value = []
    } finally {
      if (showLoading) loading.value = false
    }
  }

  async function add(skuId: string | number, quantity: number) {
    await cartApi.add(skuId, quantity)
    await fetchCart()
  }

  async function changeCount(item: CartItem, quantity: number) {
    const previous = item.count
    item.count = quantity
    try {
      await cartApi.updateCount(item.id, quantity)
      await fetchCart(false)
    } catch (error) {
      item.count = previous
      throw error
    }
  }

  async function select(item: CartItem, selected: boolean) {
    await cartApi.updateSelected([item.id], selected)
    await fetchCart(false)
  }

  async function selectAll(selected: boolean) {
    if (!items.value.length) return
    await cartApi.updateSelected(items.value.map((item) => item.id), selected)
    await fetchCart(false)
  }

  async function remove(ids: Array<string | number>) {
    await cartApi.delete(ids)
    await fetchCart(false)
  }

  function reset() {
    items.value = []
    invalidItems.value = []
  }

  return {
    items,
    invalidItems,
    loading,
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
