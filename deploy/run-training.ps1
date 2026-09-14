docker run -d `
  --name deploy-training-1 `
  --network txgy-dev `
  --network-alias training `
  --restart unless-stopped `
  -e JAVA_OPTS="-Xms256m -Xmx512m -XX:MaxMetaspaceSize=256m" `
  -e "SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL=jdbc:mysql://mysql:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true" `
  -e SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME=root `
  -e SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD=root `
  -e SPRING_DATA_REDIS_HOST=redis `
  -e SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=nacos:8848 `
  -e SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=nacos:8848 `
  -e RESOURCE_UPLOAD_DIR=/app/uploads `
  -e TXGY_CAPTCHA_ENABLE=false `
  -e TZ=Asia/Shanghai `
  -e SERVER_PORT=48080 `
  -e "TRAINING_EXAM_BASE_URL=http://module5-training-exam-1:8000" `
  deploy-training:latest