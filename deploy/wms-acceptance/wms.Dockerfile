FROM deploy-wms:latest
COPY txgy-module-wms/txgy-module-wms-server/target/txgy-module-wms-server.jar /app/app.jar
