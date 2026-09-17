import { useRoute } from 'vue-router'
import { buyerPath, buyerPayment } from '@/utils/buyer-navigation'

export function useBuyerNavigation() {
  const route = useRoute()
  return {
    buyerTo: (path: string) => buyerPath(route.path, path),
    paymentTo: (payId: number | string, orderId: number | string) => buyerPayment(route.path, payId, orderId),
  }
}
