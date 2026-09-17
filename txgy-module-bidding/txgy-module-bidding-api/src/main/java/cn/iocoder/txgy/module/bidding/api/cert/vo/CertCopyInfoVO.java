package cn.iocoder.txgy.module.bidding.api.cert.vo;

/**
 * 加注件信息（电子证照加注件元数据 · 数据集）——交换层规范 VO。
 *
 * <p>规范出处：《资质管理/资质管理规范文件/GBT+36903-2018.md》GB/T 36903—2018 §5.4「加注件信息」。
 * <p>设计遵循/针对：① 字段名照录英文名称，短名（JZJ*）与出处编号留注释；加注件信息只在形成加注件时写入文件元数据（36903 §5.1 c)）；
 * ② 全部 String（《招投标模块开发规范》§2.2 第 2 条）；加注件有效期截止时间须与 36906 §6.2 下载请求的 certificateCopyExpiringTime 呼应；
 * ③ 文件下载申请（36906 §5.2.3 权限控制）宜加注件方式提供事由+有效期，本数据集即该声明的元数据载体；
 * ④ 同 CertBaseInfoVO：不标注 @PublicLevel（见 05-规范VO表设计.md §一/§六）。
 */
public record CertCopyInfoVO(
        // GB/T 36903—2018 §5.4.1｜certificateCopyCreationTime｜JZJZZSJ 加注件制作时间｜时间型（GB/T 7408—2005）｜可选
        String certificateCopyCreationTime,
        // GB/T 36903—2018 §5.4.2｜certificateCopyCreator｜JZJZZZ 加注件制作者（单位全称/简称）｜可选
        String certificateCopyCreator,
        // GB/T 36903—2018 §5.4.3｜certificateCopyCause｜JZJZZSY 加注件制作事由（一般同步显示在水印内容）｜可选
        String certificateCopyCause,
        // GB/T 36903—2018 §5.4.4｜certificateCopyExpiringTime｜JZJYXQJZSJ 加注件有效期截止时间｜可选
        String certificateCopyExpiringTime
) {
}
