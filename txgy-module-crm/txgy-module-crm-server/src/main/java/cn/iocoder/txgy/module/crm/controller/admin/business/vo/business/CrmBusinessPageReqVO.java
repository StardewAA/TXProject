package cn.iocoder.txgy.module.crm.controller.admin.business.vo.business;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.validation.InEnum;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessStageEnum;
import cn.iocoder.txgy.module.crm.enums.business.CrmBusinessStateEnum;
import cn.iocoder.txgy.module.crm.enums.common.CrmSceneTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - 商机分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CrmBusinessPageReqVO extends PageParam {

    @Schema(description = "商机名称", example = "李四")
    private String name;

    @Schema(description = "客户编号", example = "10795")
    private Long customerId;

    @Schema(description = "联系人编号", example = "10795")
    private Long contactId;

    @Schema(description = "场景类型", example = "1")
    @InEnum(CrmSceneTypeEnum.class)
    private Integer sceneType; // 场景类型，为 null 时则表示全部

    @Schema(description = "商机阶段：1商机待评估 2初步接洽 3需求确认 4方案评估 5商务谈判", example = "3")
    @InEnum(CrmBusinessStageEnum.class)
    private Integer stage;

    @Schema(description = "商机状态组：1未开始 2进行中 3已赢单 4已丢单", example = "2")
    @InEnum(CrmBusinessStateEnum.class)
    private Integer businessState;

    @Schema(description = "是否仅查询开启中的商机（未开始 / 进行中），用于销售看板", example = "true")
    private Boolean openOnly;

    @Schema(description = "是否仅查询已关闭的商机（已赢单 / 已丢单），用于归档视图", example = "true")
    private Boolean closedOnly;

}
