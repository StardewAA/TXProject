package cn.iocoder.txgy.module.product.controller.admin.enterprise.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class ProductAdminBusinessMemberSaveReqVO {
    @NotNull private Long adminUserId;
    @NotNull private Long enterpriseId;
    /** 1 企业全部店铺，2 仅显式店铺。 */
    @NotNull private Integer scopeType;
    /** 0 启用，1 停用。 */
    @NotNull private Integer status;
    private Set<Long> shopIds;
}
