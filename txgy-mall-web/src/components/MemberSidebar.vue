<script setup lang="ts">
import OrderWorkbenchBadge from './OrderWorkbenchBadge.vue'
</script>
<template>
  <aside class="member-sidebar">
    <h3>会员中心</h3>
    <MallLink to="/member">账户概览</MallLink>
    <MallLink to="/member/orders">我的订单<OrderWorkbenchBadge /></MallLink>
    <MallLink to="/member/addresses">收货地址</MallLink>
    <MallLink to="/member/favorites">商品收藏</MallLink>
    <MallLink to="/member/history">浏览足迹</MallLink>
    <MallLink to="/member/after-sales">退款售后</MallLink>
    <MallLink to="/member/wallet">我的钱包</MallLink>
    <MallLink to="/member/customer-service">在线客服</MallLink>
  </aside>
</template>
<style scoped>
.member-sidebar{overflow:hidden;border-radius:14px}.member-sidebar h3{margin:0;padding:28px 24px;background:linear-gradient(125deg,#183d43,#27675c);color:white;letter-spacing:2px}.member-sidebar a{border-left:3px solid transparent;transition:background .2s}.member-sidebar a:hover{background:#f1f7f4}.member-sidebar a.router-link-exact-active{background:#e5f2ec;color:#147d6b;border-left-color:#147d6b;font-weight:600}
</style>
