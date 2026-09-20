import { fileURLToPath } from 'node:url';
import Vue from '@vitejs/plugin-vue';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [Vue()],
  resolve: { alias: { '#': fileURLToPath(new URL('../../../../', import.meta.url)) } },
  test: {
    environment: 'happy-dom',
    include: ['apps/web-antd/src/views/mall/promotion/kefu-scoped/*.test.ts'],
  },
});
