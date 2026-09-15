import { createApp } from 'vue';
import 'ant-design-vue/dist/reset.css';
import Review from '../../apps/web-antd/src/views/cms/review/index.vue';
const app = createApp(Review);
// Test fixture only: production still registers the real access directive and checks server permissions.
app.directive('access', () => {});
app.mount('#app');
