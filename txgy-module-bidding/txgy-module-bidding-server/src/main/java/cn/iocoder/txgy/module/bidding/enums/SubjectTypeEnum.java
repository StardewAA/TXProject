package cn.iocoder.txgy.module.bidding.enums;
// [依据] 主体角色：招标人=《招标投标法》第8条（提出招标项目、进行招标的法人或其他组织）；
//              投标人=《招标投标法》第25条（响应招标、参加投标竞争的法人或者其他组织）；
//              代理机构=《招标投标法》第13~15条（本模块扩展位，本期不落地）。
// [依据] 字段取值口径：招标人/投标人主体类别见《VO数据规范》表89 法人基本信息(LEGAL_ROLE 主体角色类型编码) 与
//              陕 V2.0 ProjectSubjectExtVO(DANWEI_TYPE 11 招标人 / 18 招标代理)；模块内先以 BIDDER/TENDERER 两角色落地。

/**
 * 主体类型（企业租户级：一企业一租户，同一租户可同时具备投标人/招标人两种主体身份）。
 */
public enum SubjectTypeEnum {

    /** 投标人（响应招标、参加投标竞争的法人或者其他组织） */
    BIDDER("投标人"),
    /** 招标人（依法提出招标项目、进行招标的法人或者其他组织） */
    TENDERER("招标人"),
    /** 招标代理机构（扩展位：本期未落地，预留枚举防止脏数据入库） */
    AGENCY("招标代理机构");

    private final String label;

    SubjectTypeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static SubjectTypeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (SubjectTypeEnum type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /** 本期支持授权/建档的类型（AGENCY 未落地不参与授权与信息完善门） */
    public static boolean isSupported(String code) {
        return BIDDER.name().equals(code) || TENDERER.name().equals(code);
    }
}
