package cn.iocoder.txgy.module.bidding.controller.admin.noticefollow.vo;
// 规范依据：设计文档 v0.6 §12.4/§13.5；《招投标模块开发规范》§3.1（VO 规约）

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "我的关注项目列表分页 Request（“关注的项目列表”界面行数据源）")
@Data
@EqualsAndHashCode(callSuper = true)
public class FollowItemPageReqVO extends PageParam {

    @Schema(description = "维度类型过滤：PROJECT/TENDER_PROJECT/SECTION（空=全部）")
    private String followType;

}
