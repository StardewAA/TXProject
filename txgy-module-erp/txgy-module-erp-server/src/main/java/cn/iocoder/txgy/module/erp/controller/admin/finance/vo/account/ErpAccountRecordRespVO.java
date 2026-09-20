package cn.iocoder.txgy.module.erp.controller.admin.finance.vo.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - ERP 结算账户资金流水 Response VO")
@Data
public class ErpAccountRecordRespVO {

    private Long id;
    private Long accountId;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private BigDecimal changeAmount;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;
    private String remark;
    private LocalDateTime createTime;

}
