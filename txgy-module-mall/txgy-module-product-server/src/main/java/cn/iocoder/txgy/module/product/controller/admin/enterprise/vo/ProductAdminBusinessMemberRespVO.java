package cn.iocoder.txgy.module.product.controller.admin.enterprise.vo;

import lombok.Data;

import java.util.Set;

@Data
public class ProductAdminBusinessMemberRespVO {
    private Long id;
    private Long adminUserId;
    private String nickname;
    private String mobile;
    private Long enterpriseId;
    private Integer scopeType;
    private Integer status;
    private Set<Long> shopIds;
}
