package cn.iocoder.txgy.module.crm.controller.admin.search.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CRM 全局检索 Response VO
 *
 * <p>用于顶部全局搜索：一次输入，跨模块返回线索、客户、联系人、商机、合同等关联数据
 *
 * @author txgycloud
 */
@Schema(description = "管理后台 - CRM 全局检索 Response VO")
@Data
public class CrmGlobalSearchRespVO {

    @Schema(description = "客户（含上游供应商）")
    private List<Item> customers;

    @Schema(description = "线索")
    private List<Item> clues;

    @Schema(description = "联系人")
    private List<Item> contacts;

    @Schema(description = "商机")
    private List<Item> businesses;

    @Schema(description = "合同")
    private List<Item> contracts;

    @Schema(description = "检索结果总数")
    private Integer total;

    /**
     * 检索结果项
     */
    @Schema(description = "管理后台 - CRM 全局检索结果项")
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @Schema(description = "编号", example = "1024")
        private Long id;

        @Schema(description = "名称", example = "长庆油田第五采油厂")
        private String name;

        @Schema(description = "业务类型", example = "customer")
        private String bizType;

        @Schema(description = "业务类型名称", example = "客户")
        private String bizTypeName;

        @Schema(description = "手机", example = "13800138000")
        private String mobile;

        @Schema(description = "电话", example = "0917-3820001")
        private String telephone;

        @Schema(description = "补充信息（客户名 / 编号 / 行业等）", example = "下游客户")
        private String extra;

        @Schema(description = "负责人的用户编号", example = "1024")
        private Long ownerUserId;

        @Schema(description = "负责人名字", example = "刘志强")
        private String ownerUserName;

    }

}
