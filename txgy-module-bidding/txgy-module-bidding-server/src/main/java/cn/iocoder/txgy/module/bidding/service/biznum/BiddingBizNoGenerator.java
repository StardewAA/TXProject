package cn.iocoder.txgy.module.bidding.service.biznum;

/**
 * 内部业务编号生成器（按规范规则生成，非随机）。
 *
 * <p>独立式（generate）：17 位 = 前缀(1) + 行政区划市级代码(6, GB/T 2260) + 年度(4) + 序列号(6, 0-9a-z 组合)，
 * 序列号段对齐《电子招标投标系统技术规范 第1部分 交易平台》附录B.3.2。
 * <p>层级式（generateChild）：20/23 位 = 父编号 + 3 位序列（0-9a-z 组合），
 * 编号自带"项目→招标项目→标段"追溯链（T08 编码标准：17+3=20、20+3=23）。
 * <p>为什么必须规则生成而非 UUID：编号要可读（看编码段知道归属区域/年度/顺序）、
 * 可排序、可检索、可对账；UUID 随机串既不可读也不可持续递增，且与规范格式不符。
 */
public interface BiddingBizNoGenerator {

    /**
     * 生成独立式内部业务编号（NOTICE / FILING / PROJECT）。
     *
     * @param type       业务类型（决定前缀）
     * @param regionCode 行政区划市级代码（GB/T 2260，6 位；为空用默认省级）
     * @return 17 位编号
     */
    String generate(BiddingBizNoTypeEnum type, String regionCode);

    /**
     * 生成层级式内部业务编号（TENDER_PROJECT / BID_SECTION）。
     *
     * @param type       业务类型（须为层级式：{@link BiddingBizNoTypeEnum#isChild()}）
     * @param parentCode 父编号（招标项目=所属项目编号 17 位；标段=所属招标项目编号 20 位）
     * @return 父编号 + 3 位序列（20/23 位）
     */
    String generateChild(BiddingBizNoTypeEnum type, String parentCode);
}
