package cn.iocoder.txgy.module.bidding.controller.admin.project.vo;

// [规范依据]《招投标模块开发规范》§3.2 + 数据规范 V2.0 数据集 TenderProjectVO
// [业务目标] 招标项目登记/修改：编号为全链路追溯主键，招标人/代理/方式等主列供追溯与展示，payload 落权威全集
// [为什么] 招标项目是"一个项目追溯全部信息"的中枢（公告、标段、备案、关注均挂其编号），主列齐备可免解析 JSON

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 招标项目 新建/修改 Request")
@Data
public class TenderProjectSaveReqVO {

    @Schema(description = "招标项目 ID；null=新建，非 null=修改")
    private Long id;

    @Schema(description = "招标项目编号；新建可不传（系统按规范生成：所属项目编号 17 位 + 3 位序列 = 20 位），保存后从详情回显；租户内唯一，追溯主键")
    private String tenderProjectCode;
    @Schema(description = "所属项目编号（关联已登记项目；可空=尚未挂接项目）")
    private String projectCode;
    // [草稿宽松 2026-09-27 契约29 §3.4] 草稿阶段不做必填校验；必填强制点 = 状态流转事件。
    @Schema(description = "招标项目名称（草稿可空；提交审批/发布时服务层按状态机强制）")
    private String tenderProjectName;
    @Schema(description = "招标项目类型（TENDER_PROJECT_TYPE）")
    private String tenderProjectType;
    @Schema(description = "行政区划（REGION_CODE）")
    private String regionCode;
    @Schema(description = "招标内容（TENDER_CONTENT）")
    private String tenderContent;
    @Schema(description = "招标人名称（TENDERER_NAME）")
    private String tendererName;
    @Schema(description = "招标人代码类型（TENDERER_CODE_TYPE）")
    private String tendererCodeType;
    @Schema(description = "招标人代码（TENDERER_CODE，通常为统一社会信用代码）")
    private String tendererCode;
    @Schema(description = "招标代理机构名称（TENDER_AGENCY_NAME）")
    private String tenderAgencyName;
    @Schema(description = "代理机构代码类型（TENDER_AGENCY_CODE_TYPE）")
    private String tenderAgencyCodeType;
    @Schema(description = "代理机构代码（TENDER_AGENCY_CODE）")
    private String tenderAgencyCode;
    @Schema(description = "招标方式（TENDER_MODE）")
    private String tenderMode;
    @Schema(description = "资格审查方式（QULIFICATION_CHECK_MODE，规范原文拼写）")
    private String qulificationCheckMode;
    @Schema(description = "招标组织形式（TENDER_ORGANIZE_FORM）")
    private String tenderOrganizeForm;
    @Schema(description = "监督部门名称（SUPERVISE_DEPT_NAME）")
    private String superviseDeptName;
    @Schema(description = "监督部门代码（SUPERVISE_DEPT_CODE）")
    private String superviseDeptCode;
    @Schema(description = "审批部门名称（APPROVE_DEPT_NAME）")
    private String approveDeptName;
    @Schema(description = "审批部门代码（APPROVE_DEPT_CODE）")
    private String approveDeptCode;
    @Schema(description = "内部管理态：ACTIVE/CLOSED")
    private String state;
    @Schema(description = "数据集字段（key=规范英文名；按 TenderProjectVO 规整补 null）")
    private Map<String, Object> payload;
    @Schema(description = "附件绑定（可选；契约29 §2.7 —— 保存时一并绑定，幂等；不传 = 不改变附件关系）")
    private List<AttachmentBindItemVO> attachments;
    @Schema(description = "备注")
    private String remark;
}
