package cn.iocoder.txgy.module.infra.api.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 文件业务绑定 Request DTO
 *
 * 由业务模块在**单据落库成功后**调用，用于把「待绑定」的文件确认为业务资源：
 * - 公开资源（商品图 / 店铺 logo 等）：{@code accessType = 1}，之后匿名可读；
 * - 私有资源（售后凭证 / 询价附件等）：{@code accessType = 2} + 参与方，之后按参与方判定。
 *
 * 安全说明：该接口是服务端内部 RPC，调用方必须已完成自身业务权限校验，
 * 且 {@code tenantId} / {@code ownerUserType} / {@code ownerUserId} 必须来自可信上下文。
 *
 * @author OPENLAB BS
 */
@Schema(description = "RPC 服务 - 文件业务绑定 Request DTO")
@Data
@Accessors(chain = true)
public class FileBindReqDTO {

    @Schema(description = "文件访问地址列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "文件访问地址不能为空")
    private List<String> urls;

    @Schema(description = "业务类型（服务端归一化名，例如 PRODUCT_IMAGE、AFTER_SALE_IMAGE）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "业务类型不能为空")
    private String bizType;

    @Schema(description = "业务单据编号")
    private String bizId;

    @Schema(description = "访问类型：1=公开 2=私有", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer accessType;

    @Schema(description = "归属租户编号")
    private Long tenantId;

    @Schema(description = "归属用户类型：1=会员 2=后台用户")
    private Integer ownerUserType;

    @Schema(description = "归属用户编号")
    private Long ownerUserId;

    @Schema(description = "可读参与方列表（私有资源）")
    private List<Participant> participants;

    @Schema(description = "参与方")
    @Data
    @Accessors(chain = true)
    public static class Participant {

        @Schema(description = "用户类型：1=会员 2=后台用户")
        private Integer userType;

        @Schema(description = "用户编号")
        private Long userId;

    }

}
