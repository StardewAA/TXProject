package cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.contract;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ErpPurchaseContractTemplateRespVO {
    private Long id;
    private String name;
    private String version;
    private String content;
    private Boolean defaultFlag;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
