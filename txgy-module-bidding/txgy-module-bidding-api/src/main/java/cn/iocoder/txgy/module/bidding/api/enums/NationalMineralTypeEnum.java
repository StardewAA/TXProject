package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 矿产种类代码集（国家表73）——<b>已下沉数据库字典</b>，本类仅保留字典类型标识。
 *
 * <p>整改背景：原 799 行实现把《VO 数据对象规范》第十章的国家矿产种类代码表（263 条）
 * 逐条照抄为 Java 常量（每条还带储量单位注释），属「写死在代码里的枚举数据」。
 * 依据用户核查结论③，数据已下沉业务字典表 {@code bidding_dict_data}（幂等种子脚本：
 * {@code sql/history/2026-09-28-national-mineral-dict.sql}），本类瘦身为字典类型常量壳。
 *
 * <p>读取方式：前端/后端一律经 {@code GET /bidding/meta/dicts?types=national_mineral_type}
 * （MetaDictServiceImpl 按字典表数据自动支持），运营改字典即改前端，无需发版。
 * 字典行：dict_value=矿产代码（11001…97130），dict_label=矿产名称，remark=储量单位（如有）。
 *
 * <p>原常量（如 {@code CODE_11001 = "11001"}）已删除；如他域仍引用本类的 CODE_* 常量，
 * 请改为读取该字典类型后按下标取值。
 */
public final class NationalMineralTypeEnum {

    /** 国家矿产种类代码字典类型（dict_value=矿产代码，dict_label=矿产名称，remark=储量单位） */
    public static final String DICT_TYPE_MINERAL_TYPE = "national_mineral_type";

    private NationalMineralTypeEnum() {
    }

}