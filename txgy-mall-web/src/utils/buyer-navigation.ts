export const isPurchasePath = (path: string) => path === '/purchase' || path.startsWith('/purchase/')

export function buyerPath(currentPath: string, memberPath: string) {
  return isPurchasePath(currentPath) ? memberPath.replace(/^\/member(?=\/|$)/, '/purchase') : memberPath
}

export function buyerPayment(currentPath: string, payId: number | string, orderId: number | string) {
  return { path: `/pay/${payId}`, query: { orderId: String(orderId), redirect: buyerPath(currentPath, `/member/orders/${orderId}`) } }
}

export const isPendingDispatch = (path: string, status: unknown) => path.endsWith('/orders') && status === '10'
