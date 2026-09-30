package cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 资格审查委员会成员响应")
@Data
public class PrequalCommitteeRespVO {

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "资格预审ID")
    private Long prequalId;

    @Schema(description = "成员姓名")
    private String memberName;

    @Schema(description = "成员类别（TENDERER_REP/TECH_EXPERT/ECON_EXPERT）")
    private String memberCategory;

    @Schema(description = "委员会内角色（LEADER/MEMBER）")
    private String memberRole;

    @Schema(description = "所在单位")
    private String memberOrg;

    @Schema(description = "职称/职务")
    private String memberTitle;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private java.time.LocalDateTime createTime;
}
