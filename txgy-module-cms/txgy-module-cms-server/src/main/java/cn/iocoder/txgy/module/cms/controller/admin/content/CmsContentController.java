package cn.iocoder.txgy.module.cms.controller.admin.content;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.cms.controller.admin.content.vo.CmsAuditReqVO;
import cn.iocoder.txgy.module.cms.framework.operatelog.CmsOperateLog;
import cn.iocoder.txgy.module.cms.dal.dataobject.Material.CmsMaterialDO;
import cn.iocoder.txgy.module.cms.dal.dataobject.Policy.CmsPolicyDO;
import cn.iocoder.txgy.module.cms.dal.dataobject.ProcessResource.CmsProcessResourceDO;
import cn.iocoder.txgy.module.cms.dal.dataobject.Quota.CmsQuotaDO;
import cn.iocoder.txgy.module.cms.dal.dataobject.Standard.CmsStandardDO;
import cn.iocoder.txgy.module.cms.dal.mysql.Material.CmsMaterialMapper;
import cn.iocoder.txgy.module.cms.dal.mysql.Policy.CmsPolicyMapper;
import cn.iocoder.txgy.module.cms.dal.mysql.ProcessResource.CmsProcessResourceMapper;
import cn.iocoder.txgy.module.cms.dal.mysql.Quota.CmsQuotaMapper;
import cn.iocoder.txgy.module.cms.dal.mysql.Standard.CmsStandardMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.cms.enums.ErrorCodeConstants.CMS_CONTENT_NOT_EXISTS;

@Tag(name = "管理后台 - CMS 内容统一审核")
@RestController
@RequestMapping("/cms/content")
@Validated
public class CmsContentController {

    @Resource
    private CmsStandardMapper standardMapper;
    @Resource
    private CmsMaterialMapper materialMapper;
    @Resource
    private CmsProcessResourceMapper processMapper;
    @Resource
    private CmsQuotaMapper quotaMapper;
    @Resource
    private CmsPolicyMapper policyMapper;

    @PutMapping("/audit")
    @Operation(summary = "统一审核（通过 / 驳回，按 contentType 分发到对应库）")
    @CmsOperateLog(type = "内容审核", subType = "审核")
    @PreAuthorize("@ss.hasPermission('cms:content:audit')")
    public CommonResult<Boolean> audit(@Valid @RequestBody CmsAuditReqVO reqVO) {
        boolean ok;
        switch (reqVO.getContentType()) {
            case "standard":
                CmsStandardDO standard = new CmsStandardDO();
                standard.setStandardId(reqVO.getContentId());
                standard.setAuditStatus(reqVO.getAuditStatus());
                standard.setAuditRemark(reqVO.getAuditRemark());
                ok = standardMapper.updateById(standard) > 0;
                break;
            case "material":
                CmsMaterialDO material = new CmsMaterialDO();
                material.setMaterialId(reqVO.getContentId());
                material.setAuditStatus(reqVO.getAuditStatus());
                material.setAuditRemark(reqVO.getAuditRemark());
                ok = materialMapper.updateById(material) > 0;
                break;
            case "process":
                CmsProcessResourceDO process = new CmsProcessResourceDO();
                process.setResourceId(reqVO.getContentId());
                process.setAuditStatus(reqVO.getAuditStatus());
                process.setAuditRemark(reqVO.getAuditRemark());
                ok = processMapper.updateById(process) > 0;
                break;
            case "quota":
                CmsQuotaDO quota = new CmsQuotaDO();
                quota.setQuotaId(reqVO.getContentId());
                quota.setAuditStatus(reqVO.getAuditStatus());
                quota.setAuditRemark(reqVO.getAuditRemark());
                ok = quotaMapper.updateById(quota) > 0;
                break;
            case "policy":
                CmsPolicyDO policy = new CmsPolicyDO();
                policy.setPolicyId(reqVO.getContentId());
                policy.setAuditStatus(reqVO.getAuditStatus());
                policy.setAuditRemark(reqVO.getAuditRemark());
                ok = policyMapper.updateById(policy) > 0;
                break;
            default:
                throw exception(CMS_CONTENT_NOT_EXISTS);
        }
        if (!ok) {
            throw exception(CMS_CONTENT_NOT_EXISTS);
        }
        return success(true);
    }

}
