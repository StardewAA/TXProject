package cn.iocoder.txgy.module.crm.enums.contract;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.txgy.framework.common.core.ArrayValuable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * CRM 合同模板类型枚举
 *
 * 不同模板对应不同的业务立场与条款结构：
 * 1) 采购合同：我方为「采购人（甲方）」，客户为「供应商（乙方）」
 * 2) 购销合同：我方为「供应商（乙方）」，客户为「采购人（甲方）」
 *
 * @author dhb52
 */
@RequiredArgsConstructor
@Getter
public enum CrmContractTemplateTypeEnum implements ArrayValuable<Integer> {

    PURCHASE(1, "采购合同", "contract/purchase-contract-template.docx"),
    SALES(2, "购销合同", "contract/sales-contract-template.docx"),
    ;

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(CrmContractTemplateTypeEnum::getType).toArray(Integer[]::new);

    /**
     * 类型
     */
    private final Integer type;
    /**
     * 名称
     */
    private final String name;
    /**
     * Word 模板资源路径
     */
    private final String templatePath;

    public static CrmContractTemplateTypeEnum getByType(Integer type) {
        if (type == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> ObjUtil.equal(item.getType(), type))
                .findFirst().orElse(null);
    }

    public static String getNameByType(Integer type) {
        CrmContractTemplateTypeEnum typeEnum = getByType(type);
        return typeEnum == null ? null : typeEnum.getName();
    }

    /**
     * 获得 Word 模板资源路径，未知类型时默认使用采购合同模板
     *
     * @param type 模板类型
     * @return 模板资源路径
     */
    public static String getTemplatePathByType(Integer type) {
        CrmContractTemplateTypeEnum typeEnum = getByType(type);
        return typeEnum == null ? PURCHASE.getTemplatePath() : typeEnum.getTemplatePath();
    }

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

}
