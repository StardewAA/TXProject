import type { Identifier, Product, ProductCollectionItem } from '@/types/mall'

export interface CollectionEntry {
  relationId: Identifier
  spuId: Identifier
  unavailable: boolean
  reason: string
  product?: Product
}

export function collectionEntry(item: ProductCollectionItem): CollectionEntry {
  const spuId = item.spuId ?? item.id
  const unavailable = item.saleable === false
  return {
    relationId: item.id, spuId, unavailable,
    reason: unavailable ? item.unavailableReason?.trim() || '商品已下架、待审核或不可购买，可移除此记录' : '',
    // Do not construct a linked product card or a fake zero price for unavailable records.
    product: unavailable ? undefined : {
      id: spuId, name: item.spuName || item.name || '商品', picUrl: item.picUrl,
      price: item.price ?? 0, stock: item.stock ?? undefined, salesCount: item.salesCount ?? undefined,
      introduction: item.introduction, shopName: item.shopName,
    },
  }
}
