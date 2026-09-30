FROM deploy-bpm:latest
COPY txgy-module-bpm/txgy-module-bpm-server/target/txgy-module-bpm-server.jar /app/app.jar
