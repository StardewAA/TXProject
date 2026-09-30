package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·获取下载地址响应（接口 getdownloadurl）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.2.2（输出参数见表 6）。
 * <p>设计遵循/针对：data.contentType 取值 “http”/“https”；data.content 为证照文件下载地址，
 * 按地址下载所得文件应符合 GB/T 36905—2018。
 */
public record EccertGetDownloadUrlRespVO(
        // GB/T 36906—2018 表6｜公共响应头
        EccertRespHeadVO head,
        // GB/T 36906—2018 表6｜下载地址 data 容器
        Data data
) {
    /**
     * 获取下载地址响应 data（GB/T 36906—2018 表 6）。
     */
    public record Data(
            // 表6｜data.contentType｜内容类型，取值为 “http” 或 “https”
            String contentType,
            // 表6｜data.certificateIdentifier｜电子证照标识（符合 GB/T 36904—2018）
            String certificateIdentifier,
            // 表6｜data.content｜证照文件下载地址
            String content
    ) {
    }
}
