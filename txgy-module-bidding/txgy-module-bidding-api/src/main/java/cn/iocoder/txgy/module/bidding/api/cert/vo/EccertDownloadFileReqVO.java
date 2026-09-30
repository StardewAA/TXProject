package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照文件下载请求（接口 downloadfile · 获取文件内容）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.2.1（输入参数见表 3）。
 * <p>设计遵循/针对：certificateIdentifier 必填（GB/T 36904）；certificateCopyCause/certificateCopyExpiringTime 可选，
 * 填写时即申请“加注件”（36906 §5.2.3 宜采用加注件方式，需提供事由与有效期截止时间；加注件元数据见 GB/T 36903—2018 §5.4）。
 */
public record EccertDownloadFileReqVO(
        // GB/T 36906—2018 §6 通用约定｜公共请求头
        EccertReqHeadVO head,
        // GB/T 36906—2018 表3｜下载请求 data 容器
        Data data
) {
    /**
     * 证照文件下载请求 data（GB/T 36906—2018 表 3）。
     */
    public record Data(
            // 表3｜data.certificateIdentifier｜电子证照标识（符合 GB/T 36904—2018）｜是
            String certificateIdentifier,
            // 表3｜data.certificateCopyCause｜加注件使用目的（如“××备案登记”）｜否
            String certificateCopyCause,
            // 表3｜data.certificateCopyExpiringTime｜加注件有效期截止时间｜否
            String certificateCopyExpiringTime
    ) {
    }
}
