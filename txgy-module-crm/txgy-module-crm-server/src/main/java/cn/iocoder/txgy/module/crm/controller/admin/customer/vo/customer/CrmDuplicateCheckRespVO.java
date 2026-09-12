package cn.iocoder.txgy.module.crm.controller.admin.customer.vo.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 查重结果 Response VO
 *
 * 用于「一键识别」后，根据手机号、公司名称匹配系统中已存在的客户、线索、联系人。
 */
@Schema(description = "管理后台 - 查重结果 Response VO")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrmDuplicateCheckRespVO {

    /** 业务类型：客户 */
    public static final String BIZ_TYPE_CUSTOMER = "customer";
    /** 业务类型：线索 */
    public static final String BIZ_TYPE_CLUE = "clue";
    /** 业务类型：联系人 */
    public static final String BIZ_TYPE_CONTACT = "contact";

    @Schema(description = "是否存在重复记录")
    private Boolean duplicated;

    @Schema(description = "命中的记录列表")
    private List<Item> items;

    @Schema(description = "管理后台 - 查重命中的记录")
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {

        @Schema(description = "业务类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "customer")
        private String bizType;

        @Schema(description = "业务类型名", requiredMode = Schema.RequiredMode.REQUIRED, example = "客户")
        private String bizTypeName;

        @Schema(description = "记录编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        private Long id;

        @Schema(description = "名称", example = "天信管业")
        private String name;

        @Schema(description = "手机号", example = "13800138000")
        private String mobile;

        @Schema(description = "负责人编号", example = "1")
        private Long ownerUserId;

        @Schema(description = "是否在公海（仅客户有效）", example = "false")
        private Boolean inPool;

        @Schema(description = "命中原因", example = "手机号一致 / 名称一致")
        private String matchReason;

    }

}
