package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§3.2 + §2.2 公开级别裁剪
// [业务目标] 招标项目台账/追溯出参

import cn.iocoder.txgy.module.bidding.controller.admin.attachment.vo.AttachmentRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

@Schema(description = "管理后台 - 招标项目 Response")
@Data
public class TenderProjectRespVO {
    private Long id;
    private String tenderProjectCode;
    private String projectCode;
    private String tenderProjectName;
    private String tenderProjectType;
    private String regionCode;
    private String tenderContent;
    private String tendererName;
    private String tendererCodeType;
    private String tendererCode;
    private String tenderAgencyName;
    private String tenderAgencyCodeType;
    private String tenderAgencyCode;
    private String tenderMode;
    private String qulificationCheckMode;
    private String tenderOrganizeForm;
    private String superviseDeptName;
    private String superviseDeptCode;
    private String approveDeptName;
    private String approveDeptCode;
    private String state;
    private String remark;
    @Schema(description = "数据集公开字段子集（@PublicLevel=SOCIAL）")
    private LinkedHashMap<String, Object> fields;
    @Schema(description = "附件行（契约29 §2.7；按 seq 升序；可为 null=未接线/无附件）")
    private List<AttachmentRespVO> attachments;
    @Schema(description = "创建时间（epoch 毫秒）")
    private Long createTime;
}
