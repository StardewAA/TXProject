package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 证照类型·基础信息（证照类型元数据 · 数据集）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36902-2018.md》GB/T 36902—2018《电子证照 目录信息规范》§5.2「基础信息」；
 * 证照类型代码编码规则见该标准 §6.3（18 位机构代码＋3 位机构内唯一码，共 21 位）。
 * <p>设计遵循/针对：① 字段名照录英文名称，中文名称/短名（ZZLX*）与出处编号留注释；
 * ② 全部 String（《招投标模块开发规范》§2.2 第 2 条）；代码长度按标准（ZZLXDM=21、ZZDYJGDM=18、GLSXDM=12）在注释标注，供后续入参校验使用；
 * ③ 证照类型=证照目录的信息实体（36902 §6.4），本数据集是证照分类/编目交换的载体；
 * ④ 同其他交换层规范 VO：不标注 @PublicLevel（见 05-规范VO表设计.md §一/§六）。
 */
public record CertTypeInfoVO(
        // GB/T 36902—2018 §5.2.1｜certificateTypeName｜ZZLXMC 证照类型名称｜字符串·自由文本｜必选｜可用作该类证照默认名称
        String certificateTypeName,
        // GB/T 36902—2018 §5.2.2｜certificateTypeCode｜ZZLXDM 证照类型代码｜字符串·21 位｜必选｜见 36902 §6.3（如 11100000000013338W002）
        String certificateTypeCode,
        // GB/T 36902—2018 §5.2.3｜certificateDefineAuthorityName｜ZZDYJG 证照定义机构（全称/简称）｜必选
        String certificateDefineAuthorityName,
        // GB/T 36902—2018 §5.2.4｜certificateDefineAuthorityCode｜ZZDYJGDM 证照定义机构代码｜字符串·18 位（GB 32100）｜必选
        String certificateDefineAuthorityCode,
        // GB/T 36902—2018 §5.2.5｜certificateDefineAuthorityLevel｜ZZDYJGJB 证照定义机构级别｜可选｜国家级/省部级/地市级/区县级/其他
        String certificateDefineAuthorityLevel,
        // GB/T 36902—2018 §5.2.6｜relatedItemName｜GLSXMC 关联事项名称（生成该类型证照的服务事项）｜可选
        String relatedItemName,
        // GB/T 36902—2018 §5.2.7｜relatedItemCode｜GLSXDM 关联事项代码｜字符串·12 位｜可选
        String relatedItemCode,
        // GB/T 36902—2018 §5.2.8｜certificateHolderCategory｜MXCZZTLX 持证主体类别｜必选｜自然人/法人和其他组织/混合/其他
        String certificateHolderCategory,
        // GB/T 36902—2018 §5.2.9｜validityRange｜YXQXFW 有效期限范围｜可选｜多个用“、”分隔；长期=“长期”
        String validityRange,
        // GB/T 36902—2018 §5.2.10｜certificateIssuingAuthorityLevel｜ZZBFJGJB 证照颁发机构级别｜可选｜国家级/省级/地市级/区县级/乡镇级/其他
        String certificateIssuingAuthorityLevel
) {
}
