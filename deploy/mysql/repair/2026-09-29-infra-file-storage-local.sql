-- infra 文件服务：主存储由「七牛云」切到「本地存储」
--
-- 背景：发放 Offer 时会生成《录用通知书》PDF，先要上传到 infra 文件服务拿到附件地址，
--   再由邮件带着这个地址发给候选人。该上传走的正是 infra 的**主存储**，而主存储一直是
--   七牛云（infra_file_config id=22，bucket ruoyi-vue-pro，域名 test.txgy.iocoder.cn），
--   实测返回 403：
--
--     software.amazon.awssdk.services.s3.model.S3Exception:
--       There is a problem with your account that prevents the operation from
--       completing successfully. (Status Code: 403)
--
--   → HRM 侧表现为 FileApi.createFile 抛「系统异常」，被 sendOfferNoticeMail 的
--     catch(Throwable) 吞掉，落成 offer.notify_status=2，界面上就是「Offer 通知发不出去」。
--   同一原因也让那批 test.txgy.iocoder.cn 的老附件在 infra 预览接口上全部取不回来。
--
-- 本脚本只改「主存储指向」，不动七牛那条配置本身（保留 id=22，将来账号恢复可直接切回）。
--
-- 为什么要动 basePath：本地客户端的路径是写死在配置里的。上游默认值
--   /Users/yunai/tmp/file 是 macOS 演示路径，容器里不存在。改用 /root/uploads/infra ——
--   与 docker-compose.local.yml 里 infra 服务新增的挂载
--   `./uploads/infra:/root/uploads/infra` 一致，落宿主机文件，容器重建不丢。
--   与 hrm 服务的 ./uploads/hrm 保持同一约定（/root/uploads/<模块>）。
--
-- domain 保持 http://127.0.0.1:48080 不用改：48080 是**网关**（docker ps 可见
--   txgy-gateway 0.0.0.0:48080->48080），本地客户端拼 URL 时会自动补上
--   /admin-api/infra/file/{id}/get/ 前缀（AbstractFileClient.formatFileUrl），
--   于是得到 http://127.0.0.1:48080/admin-api/infra/file/29/get/<path>，
--   正好落在网关 /admin-api/infra/** 路由 + FileController 的 /{configId}/get/** 上。
--
-- 注意：改的是 infra（公共框架）的存储配置，影响**所有模块**的文件上传，不只是 HRM。
--   切到本地后文件存在单机磁盘上，多实例部署会读不到对方写的文件 —— 生产环境应换回
--   对象存储。本环境是单机开发栈，可以这么用。
--
-- 执行后需重启 infra 服务：文件客户端在启动时构建并缓存，直接改库不会自动刷新。
--   docker compose -f deploy/local/docker-compose.local.yml --env-file deploy/.env up -d --force-recreate --no-deps infra

SET NAMES utf8mb4;

-- 1) 本地存储（id=29）：basePath 指向容器内挂载点，domain 保持网关地址
UPDATE `infra_file_config`
SET `config` = '{"@class":"cn.iocoder.txgy.module.infra.framework.file.core.client.local.LocalFileClientConfig","basePath":"/root/uploads/infra","domain":"http://127.0.0.1:48080"}',
    `updater` = 'local-switch',
    `update_time` = NOW()
WHERE `id` = 29;

-- 2) 主存储交给本地（id=29），七牛（id=22）保留配置但不再当默认
UPDATE `infra_file_config` SET `master` = b'0', `updater` = 'local-switch', `update_time` = NOW() WHERE `id` = 22;
UPDATE `infra_file_config` SET `master` = b'1', `updater` = 'local-switch', `update_time` = NOW() WHERE `id` = 29;

-- 核对：应只有 id=29 一行 master=1，且 basePath 为挂载点
-- SELECT id, name, storage, master + 0 AS is_master, config
-- FROM infra_file_config WHERE master = b'1' OR id IN (22, 29);

-- 回滚（把主存储切回七牛）：两行的 master 互换即可，basePath 无需还原
-- UPDATE infra_file_config SET master = b'0' WHERE id = 29;
-- UPDATE infra_file_config SET master = b'1' WHERE id = 22;
