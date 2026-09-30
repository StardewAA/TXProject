package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * ShaanxiSectionClassifyLevel3_1Enum——已下沉数据库字典，本类仅保留字典类型标识。
 *
 * <p>整改背景：原实现将《VO 数据对象规范》第十章代码表（4 条）逐条照抄为 Java 常量，
 * 属「写死在代码里的枚举数据」。依据用户核查结论③，数据已下沉业务字典表 {@code bidding_dict_data}（幂等种子：
 * {@code sql/history/2026-09-28-bidding-code-set-dict.sql}），本类瘦身为字典类型常量壳。
 *
 * <p>读取方式：GET /bidding/meta/dicts?types=shaanxi_section_classify_level3_1（MetaDictServiceImpl 按字典表数据自动支持）。
 *
 * <p>原 CODE_* 常量已删除；如他域仍引用，请改为读取该字典类型。
 */
public final class ShaanxiSectionClassifyLevel3_1Enum {

    /** 字典类型 code：shaanxi_section_classify_level3_1 */
    public static final String DICT_TYPE_SHAANXI_SECTION_CLASSIFY_LEVEL3_1 = "shaanxi_section_classify_level3_1";

    private ShaanxiSectionClassifyLevel3_1Enum() {
    }

}
