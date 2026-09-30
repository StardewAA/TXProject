package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 电子证照共享服务·证照文件下载响应（接口 downloadfile · 获取文件内容）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36906-2018.md》GB/T 36906—2018 §6.2.1（输出参数见表 4）。
 * <p>设计遵循/针对：data.content 为 base64 字符串（contentType 固定 “base64”），解码后文件应符合 GB/T 36905—2018（OFD 版式、含元数据与标识、数字签名/签章）。
 */
public record EccertDownloadFileRespVO(
        // GB/T 36906—2018 表4｜公共响应头
        EccertRespHeadVO head,
        // GB/T 36906—2018 表4｜下载内容 data 容器
        Data data
) {
    /**
     * 证照文件下载响应 data（GB/T 36906—2018 表 4）。
     */
    public record Data(
            // 表4｜data.contentType｜内容类型，固定为 “base64”
            String contentType,
            // 表4｜data.certificateIdentifier｜电子证照标识（符合 GB/T 36904—2018）
            String certificateIdentifier,
            // 表4｜data.content｜base64 字符串，解码后符合 GB/T 36905—2018
            String content
    ) {
    }
}
