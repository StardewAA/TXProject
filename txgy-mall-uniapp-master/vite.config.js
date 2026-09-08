import { loadEnv } from 'vite';
import uni from '@dcloudio/vite-plugin-uni';
import path from 'path';
import { fileURLToPath, URL } from 'node:url';
// import viteCompression from 'vite-plugin-compression';
import uniReadPagesV3Plugin from './sheep/router/utils/uni-read-pages-v3';
import mpliveMainfestPlugin from './sheep/libs/mplive-manifest-plugin';

// This project keeps App.vue, pages and sheep/ at the repository root. The
// uni CLI defaults to `<root>/src` when that directory exists, which makes
// imports such as `@/sheep/api` resolve to the incomplete src/sheep tree.
process.env.UNI_INPUT_DIR = fileURLToPath(new URL('./', import.meta.url));

// https://vitejs.dev/config/
export default (command, mode) => {
	const env = loadEnv(mode, __dirname, 'SHOPRO_');
	return {
		resolve: {
			alias: {
				'@': fileURLToPath(new URL('./', import.meta.url)),
				// uni-app uses `@` for the optional `src` directory when it exists.
				// The project keeps its runtime modules in the repository-level sheep/
				// directory, so pin this more specific alias before the generic one.
				'@/sheep': fileURLToPath(new URL('./sheep', import.meta.url)),
				sheep: fileURLToPath(new URL('./sheep', import.meta.url)),
			},
		},
		envPrefix: "SHOPRO_",
		plugins: [
			uni(),
			// viteCompression({
			// 	verbose: false
			// }),
			uniReadPagesV3Plugin({
				pagesJsonDir: path.resolve(__dirname, './pages.json'),
				includes: ['path', 'aliasPath', 'name', 'meta'],
			}),
			mpliveMainfestPlugin(env.SHOPRO_MPLIVE_ON)
		],
		server: {
			host: true,
			strictPort: true,
			port: Number(env.SHOPRO_DEV_PORT) || 5174,
			hmr: {
				host: 'localhost',
				clientPort: Number(env.SHOPRO_DEV_PORT) || 5174,
				port: Number(env.SHOPRO_DEV_PORT) || 5174,
				protocol: 'ws',
				overlay: true,
			},
		},
	};
};
