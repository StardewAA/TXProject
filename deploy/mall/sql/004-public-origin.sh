#!/bin/sh
# MySQL entrypoint executes this only on a fresh database. JSON escaping is done
# by MySQL and the value is passed as hex, never interpolated as SQL syntax.
set -eu
origin=${MALL_API_ORIGIN:-http://localhost:48080}
origin_hex=$(printf '%s' "$origin" | od -An -v -tx1 | tr -d ' \n')
oss_origin=${OSS_PUBLIC_ORIGIN:-https://java-ai-comency.oss-cn-beijing.aliyuncs.com}
oss_hex=$(printf '%s' "$oss_origin" | od -An -v -tx1 | tr -d ' \n')
bucket_hex=$(printf '%s' "${OSS_BUCKET:-java-ai-comency}" | od -An -v -tx1 | tr -d ' \n')
endpoint_hex=$(printf '%s' "${OSS_ENDPOINT:-oss-cn-beijing.aliyuncs.com}" | od -An -v -tx1 | tr -d ' \n')
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 ruoyi-vue-pro <<SQL
UPDATE infra_file_config SET config=JSON_SET(config,'\$.domain',CONVERT(0x${origin_hex} USING utf8mb4)) WHERE id=4;
UPDATE infra_file_config SET config=JSON_SET(config,'\$.domain',CONVERT(0x${oss_hex} USING utf8mb4),'\$.bucket',CONVERT(0x${bucket_hex} USING utf8mb4),'\$.endpoint',CONVERT(0x${endpoint_hex} USING utf8mb4)) WHERE id=36;
UPDATE product_spu SET pic_url=REPLACE(pic_url,'https://java-ai-comency.oss-cn-beijing.aliyuncs.com',CONVERT(0x${oss_hex} USING utf8mb4)),slider_pic_urls=REPLACE(slider_pic_urls,'https://java-ai-comency.oss-cn-beijing.aliyuncs.com',CONVERT(0x${oss_hex} USING utf8mb4));
UPDATE product_sku SET pic_url=REPLACE(pic_url,'https://java-ai-comency.oss-cn-beijing.aliyuncs.com',CONVERT(0x${oss_hex} USING utf8mb4));
UPDATE infra_file SET url=CONCAT(CONVERT(0x${oss_hex} USING utf8mb4),'/',path) WHERE config_id=36;
SQL
