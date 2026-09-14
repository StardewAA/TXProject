FROM nginx:alpine
COPY deploy/wms-acceptance/nginx.conf /etc/nginx/conf.d/default.conf
COPY txgy-ui-admin-vben/apps/web-antd/dist/ /usr/share/nginx/html/
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
