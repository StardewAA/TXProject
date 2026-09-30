package cn.iocoder.txgy.module.bidding.controller.admin.ca.vo;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - CA 签章台账分页入参")
@Data
@EqualsAndHashCode(callSuper = true)
public class CaSealPageReqVO extends PageParam {

    @Schema(description = "原文件名（模糊）", example = "投标文件.pdf")
    private String documentName;

    @Schema(description = "签章单位（模糊）", example = "本地CA测试单位")
    private String orgName;

    @Schema(description = "验签状态（UNVERIFIED 未验 / PASS 通过 / FAIL 不通过）", example = "PASS")
    private String verifyStatus;

    @Schema(description = "关联投标流程跟踪 id（businessCode 解析；查某次投标的全部签章）", example = "90001")
    private Long trackId;
}
