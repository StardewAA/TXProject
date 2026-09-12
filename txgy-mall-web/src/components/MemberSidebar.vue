<template>
  <aside class="member-sidebar">
    <h3>会员中心</h3>
    <RouterLink to="/member">账户概览</RouterLink>
    <RouterLink to="/member/orders">我的订单</RouterLink>
    <RouterLink to="/member/addresses">收货地址</RouterLink>
    <RouterLink to="/member/favorites">商品收藏</RouterLink>
    <RouterLink to="/member/history">浏览足迹</RouterLink>
    <RouterLink to="/member/after-sales">退款售后</RouterLink>
    <RouterLink to="/member/wallet">我的钱包</RouterLink>
    <RouterLink to="/member/customer-service">在线客服</RouterLink>
  </aside>
</template>
<style scoped>
.member-sidebar{overflow:hidden;border-radius:14px}.member-sidebar h3{margin:0;padding:28px 24px;background:linear-gradient(125deg,#183d43,#27675c);color:white;letter-spacing:2px}.member-sidebar a{border-left:3px solid transparent;transition:background .2s}.member-sidebar a:hover{background:#f1f7f4}.member-sidebar a.router-link-exact-active{background:#e5f2ec;color:#147d6b;border-left-color:#147d6b;font-weight:600}
</style>
