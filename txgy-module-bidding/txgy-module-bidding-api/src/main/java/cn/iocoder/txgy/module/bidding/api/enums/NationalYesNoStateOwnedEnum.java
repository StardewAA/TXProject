package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * NationalYesNoStateOwnedEnum——已下沉数据库字典，本类仅保留字典类型标识。
 *
 * <p>整改背景：原实现将《VO 数据对象规范》第十章代码表（2 条）逐条照抄为 Java 常量，
 * 属「写死在代码里的枚举数据」。依据用户核查结论③，数据已下沉业务字典表 {@code bidding_dict_data}（幂等种子：
 * {@code sql/history/2026-09-28-bidding-code-set-dict.sql}），本类瘦身为字典类型常量壳。
 *
 * <p>读取方式：GET /bidding/meta/dicts?types=national_yes_no_state_owned（MetaDictServiceImpl 按字典表数据自动支持）。
 *
 * <p>原 CODE_* 常量已删除；如他域仍引用，请改为读取该字典类型。
 */
public final class NationalYesNoStateOwnedEnum {

    /** 字典类型 code：national_yes_no_state_owned */
    public static final String DICT_TYPE_NATIONAL_YES_NO_STATE_OWNED = "national_yes_no_state_owned";

    private NationalYesNoStateOwnedEnum() {
    }

}
