package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·获取下载地址请求（接口 getdownloadurl）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.2.2（输入参数见表 5）。
 * <p>设计遵循/针对：入参与 downloadfile（表 3）一致（certificateIdentifier 必填，加注件事由/有效期可选），
 * 响应返回下载地址而非内容（见 EccertGetDownloadUrlRespVO）。
 */
public record EccertGetDownloadUrlReqVO(
        // GB/T 36906—2018 §6 通用约定｜公共请求头
        EccertReqHeadVO head,
        // GB/T 36906—2018 表5｜请求 data 容器
        Data data
) {
    /**
     * 获取下载地址请求 data（GB/T 36906—2018 表 5）。
     */
    public record Data(
            // 表5｜data.certificateIdentifier｜电子证照标识（符合 GB/T 36904—2018）｜是
            String certificateIdentifier,
            // 表5｜data.certificateCopyCause｜加注件使用目的｜否
            String certificateCopyCause,
            // 表5｜data.certificateCopyExpiringTime｜加注件有效期截止时间｜否
            String certificateCopyExpiringTime
    ) {
    }
}
