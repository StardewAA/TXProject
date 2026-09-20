package cn.iocoder.txgy.module.mes.api.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RPC 服务 - ERP 推送往来企业（客户/供应商）主数据到 MES 的请求
 *
 * 编码 {@link #partnerCode} 使用 ERP 权威往来企业编码，MES 侧按 partnerType 分别落库为
 * {@code mes_md_client.code} 或 {@code mes_md_vendor.code}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesPartnerUpsertReqDTO extends MesIntegrationBaseReqDTO {

    @NotBlank(message = "partnerCode 不能为空")
    private String partnerCode;

    @NotBlank(message = "企业名称不能为空")
    private String name;

    /**
     * 往来企业类型，取值 CUSTOMER / SUPPLIER
     */
    @NotBlank(message = "partnerType 不能为空")
    private String partnerType;

    private String contact;
    private String mobile;
    private String telephone;
    private String email;
    private String taxNo;
    private String remark;

    @NotBlank(message = "status 不能为空")
    private String status;

    @NotNull(message = "dataVersion 不能为空")
    private Long dataVersion;

}
