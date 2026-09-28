package cn.iocoder.txgy.module.bidding.enums;

import java.util.Arrays;

/**
 * 学历层次（bidding_personnel_profile.edu_level 的 code 取值；带可比序号用于"≥"判定）。
 *
 * <p>规范出处：《陕西省公共资源交易电子监管系统数据规范 V2.0》学历字段（C..50 自由文本，实际按层次归类）；
 * 《建筑业企业资质标准》（建市〔2014〕159号）技术负责人学历要求（如"大专以上"）。
 * <p>为什么这样写：招聘/资质要求常写"大专及以上"，必须可比较——用 rank 把层次序数化，
 * 避免在服务里写字符串枚举比较。
 * <p>业务目标：投标配证时按招标文件学历门槛自动过滤候选人。
 */
public enum PersonnelEduLevelEnum {

    /** 博士 */
    DOCTOR(5),
    /** 硕士 */
    MASTER(4),
    /** 本科 */
    BACHELOR(3),
    /** 大专 */
    COLLEGE(2),
    /** 中专/高中及以下 */
    SECONDARY(1),
    /** 其他/未填 */
    OTHER(0);

    private final int rank;

    PersonnelEduLevelEnum(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /** 解析层次序号；未知返回 -1（调用方按"不满足比较"处理，未知不等于满足）。 */
    public static int rankOf(String code) {
        if (code == null) {
            return -1;
        }
        return Arrays.stream(values()).filter(v -> v.name().equals(code)).findFirst()
                .map(PersonnelEduLevelEnum::getRank).orElse(-1);
    }
}
