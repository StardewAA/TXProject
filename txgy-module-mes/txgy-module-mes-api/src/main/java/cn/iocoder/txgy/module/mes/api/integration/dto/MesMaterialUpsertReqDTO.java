package cn.iocoder.txgy.module.mes.api.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * RPC 服务 - ERP 推送物料主数据到 MES 的请求
 *
 * 编码 {@link #materialCode} 使用 ERP 权威物料编码，MES 侧直接落库为 {@code mes_md_item.code}，
 * 以此打通两端物料主数据编码。
 *
 * 仅包含 MES 侧可存储的字段：物料分类、计量单位、安全库存等生产属性仍归 MES 自主维护，
 * 推送不覆盖这些字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MesMaterialUpsertReqDTO extends MesIntegrationBaseReqDTO {

    @NotBlank(message = "materialCode 不能为空")
    private String materialCode;

    @NotBlank(message = "物料名称不能为空")
    private String name;

    /**
     * 规格型号
     */
    private String specification;

    private String remark;

    @NotBlank(message = "status 不能为空")
    private String status;

    @NotNull(message = "dataVersion 不能为空")
    private Long dataVersion;

}
