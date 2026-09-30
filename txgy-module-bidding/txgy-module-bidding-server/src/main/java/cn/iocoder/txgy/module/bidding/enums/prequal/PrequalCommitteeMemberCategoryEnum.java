package cn.iocoder.txgy.module.bidding.enums.prequal;

/**
 * 资格审查委员会成员类别。
 *
 * <p>设计依据：《实施条例》第37条（评标委员会组成，资格审查委员会适用）：由招标人的代表和
 * 有关技术、经济等方面的专家组成。
 */
public enum PrequalCommitteeMemberCategoryEnum {

    /** 招标人代表 */
    TENDERER_REP("招标人代表"),
    /** 技术专家 */
    TECH_EXPERT("技术专家"),
    /** 经济专家 */
    ECON_EXPERT("经济专家");

    private final String label;

    PrequalCommitteeMemberCategoryEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 专家（技术/经济）是否占成员总数 ≥2/3（条例第37条）。 */
    public static boolean expertQuorumMet(long total, long expertCount) {
        if (total <= 0) {
            return false;
        }
        return expertCount * 3L >= total * 2L;
    }
}
