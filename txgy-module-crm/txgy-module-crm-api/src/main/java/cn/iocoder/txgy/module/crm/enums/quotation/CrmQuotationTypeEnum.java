package cn.iocoder.txgy.module.crm.enums.quotation;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import cn.iocoder.txgy.module.crm.enums.contract.CrmContractTemplateTypeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 报价类型枚举
 *
 * 业务含义：
 * 1) 购销报价：关联「下游客户 + 商机」，审批通过后转「购销合同」（我方为乙方，向客户销售）
 * 2) 采购报价：仅可选择「上游供应商」（客户类别=2），禁用客户/商机字段，审批通过后转「采购合同」（我方为甲方）
 *
 * 注意：本枚举的取值与 {@link CrmContractTemplateTypeEnum} 的取值「数值恰好相反」，
 * 请务必通过 {@link #toContractTemplateType(Integer)} 做显式映射，不要直接互相赋值：
 * 购销报价(1) → 购销合同(2)；采购报价(2) → 采购合同(1)</p>
 *
 * @author 防腐保温项目组
 */
@RequiredArgsConstructor
@Getter
public enum CrmQuotationTypeEnum implements ArrayValuable<Integer> {

    SALES(1, "购销报价"),
    PURCHASE(2, "采购报价"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmQuotationTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 名称
     */
    private final String name;

    public static CrmQuotationTypeEnum getByType(Integer type) {
        if (type == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> ObjUtil.equal(item.getType(), type))
                .findFirst().orElse(null);
    }

    public static String getNameByType(Integer type) {
        CrmQuotationTypeEnum typeEnum = getByType(type);
        return typeEnum == null ? null : typeEnum.getName();
    }

    /**
     * 是否为采购报价
     *
     * @param type 报价类型
     * @return 是否采购报价
     */
    public static boolean isPurchase(Integer type) {
        return ObjUtil.equal(PURCHASE.getType(), type);
    }

    /**
     * 报价类型 → 合同模板类型
     *
     * 购销报价(1) → 购销合同(2)；采购报价(2) → 采购合同(1)
     *
     * @param quotationType 报价类型
     * @return 合同模板类型
     */
    public static Integer toContractTemplateType(Integer quotationType) {
        return isPurchase(quotationType)
                ? CrmContractTemplateTypeEnum.PURCHASE.getType()
                : CrmContractTemplateTypeEnum.SALES.getType();
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
