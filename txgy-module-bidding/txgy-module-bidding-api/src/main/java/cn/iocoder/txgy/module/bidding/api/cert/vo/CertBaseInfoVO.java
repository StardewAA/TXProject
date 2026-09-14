package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 证照基础信息（电子证照元数据 · 数据集）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36903-2018.md》GB/T 36903—2018《电子证照 元数据规范》§5.2「基础信息」；
 * 字段取值/值域/约束照录标准（证书示例另见 GBT+36905-2018.md 附录 A）。
 * <p>设计遵循/针对：① 字段名照录标准英文名称（certificateName…），中文名称、短名（ZZMC…）、出处编号留在注释，便于回溯与 OFD 文件元数据（36905 §5）逐字对齐；
 * ② 交换层字段统一 String（《招投标模块开发规范》§2.2 第 2 条），时间/长期等格式以标准值域为准（如有效期截止=“长期”）；
 * ③ 证照标识 certificateIdentifier 为 GB/T 36904—2018 单串（6 段组合编码），不拆段建模；
 * ④ 本数据集供与其他电子证件系统对接（共享服务 queryinfo/verifyinfo 等）与证照元数据字典使用，不对外发布，故不标注 @PublicLevel（见 05-规范VO表设计.md §一/§六）。
 * <p>本文件属 api 契约层（仅依赖 txgy-common），不依赖 server 各层（《招投标模块开发规范》§4.2；ArchUnit API 隔离）。
 */
public record CertBaseInfoVO(
        // GB/T 36903—2018 §5.2.1｜certificateName｜ZZMC 证照名称｜字符串·自由文本｜必选｜例：建筑工程施工许可证
        String certificateName,
        // GB/T 36903—2018 §5.2.2｜certificateTypeCode｜ZZLXDM 证照类型代码｜字符串·21 位｜必选｜编码规则见 GB/T 36902—2018 §6.3
        String certificateTypeCode,
        // GB/T 36903—2018 §5.2.3｜certificateNumber｜ZZBH 证照编号｜字符串（按各类型证照/颁发机构规则）｜必选
        String certificateNumber,
        // GB/T 36903—2018 §5.2.4｜certificateIdentifier 证照标识（标准未给英文名/短名，英文名沿用 36905 附录A 与 36906 报文）｜必选｜编码见 GB/T 36904—2018
        String certificateIdentifier,
        // GB/T 36903—2018 §5.2.5｜certificateIssuingAuthorityName｜ZZBFJG 证照颁发机构｜字符串｜必选｜多个用“、”分隔
        String certificateIssuingAuthorityName,
        // GB/T 36903—2018 §5.2.6｜certificateIssuingAuthorityCode｜ZZBFJGDM 证照颁发机构代码｜字符串（统一社会信用代码 GB 32100）｜可选
        String certificateIssuingAuthorityCode,
        // GB/T 36903—2018 §5.2.7｜certificateIssuedDate｜ZZBFRQ 证照颁发日期｜字符串（年月日标全或 GB/T 7408—2005）｜必选
        String certificateIssuedDate,
        // GB/T 36903—2018 §5.2.8｜certificateHolderName｜CZZT 持证主体｜字符串（与照面一致）｜必选｜多个用“、”分隔
        String certificateHolderName,
        // GB/T 36903—2018 §5.2.9｜certificateHolderCode｜CZZTDM 持证主体代码｜字符串（统一社会信用代码/身份证件号码等）｜可选｜多个用“、”分隔
        String certificateHolderCode,
        // GB/T 36903—2018 §5.2.10｜certificateHolderTypeName｜CZZTDMLX 持证主体代码类型（中文名）｜可选｜多个用“、”分隔
        String certificateHolderTypeName,
        // GB/T 36903—2018 §5.2.11｜certificateHolderTypeCode｜CZZTDMLXDM 持证主体代码类型代码｜可选｜取值见 36903 附录 A（如 001=统一社会信用代码、111=公民身份号码）
        String certificateHolderTypeCode,
        // GB/T 36903—2018 §5.2.12｜certificateEffectiveDate｜ZZYXQQSRQ 证照有效期起始日期｜可选
        String certificateEffectiveDate,
        // GB/T 36903—2018 §5.2.13｜certificateExpiringDate｜ZZYXQJZRQ 证照有效期截止日期｜可选｜长期有效取值“长期”
        String certificateExpiringDate
) {
}
