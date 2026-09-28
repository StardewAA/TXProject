package cn.iocoder.txgy.module.bidding.enums.processtrack;

/**
 * 开标房间成员身份。
 */
public enum OpeningMemberRoleEnum {

    /** 招标方 */
    TENDERER,
    /** 平台方（开标主持人） */
    PLATFORM,
    /** 投标人 */
    BIDDER,
    /** 监督方（政府监管） */
    SUPERVISOR;

    public static OpeningMemberRoleEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (OpeningMemberRoleEnum item : values()) {
            if (item.name().equals(code)) {
                return item;
            }
        }
        return null;
    }
}
