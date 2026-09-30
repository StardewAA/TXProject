package cn.iocoder.txgy.module.bidding.service.biznum;

/**
 * 内部业务编号类型（决定编号的前缀段与生成方式）。
 *
 * <p>前缀为何用字母：外部权威编号（交易平台 PROJECT_CODE / 招标项目编号 / 标段编号，C17）
 * 以行政区划数字开头，内部编号以字母前缀开头，**字符串空间天然隔离**，
 * 即使两种编号并存也不会撞号、便于一眼区分来源。
 * <p>独立式（17 位）：前缀 + 区划 + 年度 + 6 位序列，按 (年度_区划) 分桶；
 * 层级式（20/23 位）：父编号 + 3 位序列，按父编号分桶（编号自带"项目→招标项目→标段"追溯链）。
 */
public enum BiddingBizNoTypeEnum {

    /** 内部公告编号（bidding_notice.notice_code） */
    NOTICE("B", "公告", false),
    /** 备案受理回执号（备案受理号） */
    FILING("F", "备案回执", false),
    /** 内部管理项目编号（bidding_project.project_code，无外部编号场景） */
    PROJECT("I", "内部项目", false),
    /** 招标项目编号：20 位 = 所属项目编号(17) + 3 位序列（T08：项目编号+招标项目扩展3位） */
    TENDER_PROJECT("T", "招标项目", true),
    /** 标段（包）编号：23 位 = 所属招标项目编号(20) + 3 位序列（T08：招标项目编号+标段扩展3位） */
    BID_SECTION("S", "标段", true),
    /** 资格预审编号（bidding_prequal.prequal_code，独立式 17 位） */
    PREQUAL("P", "资格预审", false);

    private final String prefix;
    private final String label;
    /** true=层级式编号（父编号+3 位序列）；false=独立式编号（前缀+区划+年度+6 位序列） */
    private final boolean child;

    BiddingBizNoTypeEnum(String prefix, String label, boolean child) {
        this.prefix = prefix;
        this.label = label;
        this.child = child;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getLabel() {
        return label;
    }

    public boolean isChild() {
        return child;
    }
}
