package cn.iocoder.txgy.module.cms.controller.admin.search;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.iocoder.txgy.module.cms.controller.admin.search.vo.CmsSearchPageReqVO;
import cn.iocoder.txgy.module.cms.controller.admin.search.vo.CmsSearchRespVO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - CMS 智能查询")
@RestController
@RequestMapping("/cms/search")
@Validated
public class CmsSearchController {

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

    @GetMapping("/page")
    @Operation(summary = "跨库统一查询（类型 + 关键词必填，缺一返回空列表）")
    @PreAuthorize("@ss.hasPermission('cms:content:query')")
    public CommonResult<PageResult<CmsSearchRespVO>> searchPage(@Valid CmsSearchPageReqVO reqVO) {
        String type = reqVO.getType();
        String keyword = reqVO.getKeyword();
        // 类型为空返回空列表；law/finance 支持年份等高级筛选（分支内再判空条件），
        // 其余类型维持「类型+关键词必填，缺一返回空列表」规则，防止全表查询
        if (type == null || type.isEmpty()) {
            return success(PageResult.empty());
        }
        if (!"law".equals(type) && !"finance".equals(type)
                && (keyword == null || keyword.trim().isEmpty())) {
            return success(PageResult.empty());
        }
        List<CmsSearchRespVO> records;
        long total;
        // 二期调整1：内容类型重构——法律法规 / 金融政策 统一查 cms_policy 按 content_type 区分
        if ("law".equals(type) || "finance".equals(type)) {
            String contentType = "law".equals(type) ? "法律法规" : "金融政策";
            // 关键词、年份、状态至少一个筛选条件，防止无条件全表查询
            boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
            if (!hasKeyword && reqVO.getPublishYear() == null
                    && reqVO.getAuditStatus() == null && reqVO.getPublishStatus() == null) {
                return success(PageResult.empty());
            }
            LambdaQueryWrapper<CmsPolicyDO> wrapper = new LambdaQueryWrapper<CmsPolicyDO>()
                    .eq(CmsPolicyDO::getContentType, contentType);
            if (hasKeyword) {
                wrapper.and(w -> w.like(CmsPolicyDO::getTitle, keyword)
                        .or().like(CmsPolicyDO::getContent, keyword));
            }
            if (reqVO.getAuditStatus() != null) {
                wrapper.eq(CmsPolicyDO::getAuditStatus, reqVO.getAuditStatus());
            }
            if (reqVO.getPublishStatus() != null) {
                wrapper.eq(CmsPolicyDO::getPublishStatus, reqVO.getPublishStatus());
            }
            // 高级筛选：发布年份
            if (reqVO.getPublishYear() != null) {
                wrapper.apply("YEAR(publish_date) = {0}", reqVO.getPublishYear());
            }
            wrapper.orderByDesc(CmsPolicyDO::getPolicyId);
            PageResult<CmsPolicyDO> page = policyMapper.selectPage(reqVO, wrapper);
            records = new ArrayList<>();
            for (CmsPolicyDO d : page.getList()) {
                records.add(build(d.getPolicyId(), type, d.getTitle(),
                        d.getAuditStatus(), d.getPublishStatus(), d.getCreateTime()));
            }
            return success(new PageResult<>(records, page.getTotal()));
        }
        switch (type) {
            case "standard":
                PageResult<CmsStandardDO> standardPage = standardMapper.selectPage(reqVO,
                        new LambdaQueryWrapperX<CmsStandardDO>()
                                .likeIfPresent(CmsStandardDO::getStandardName, keyword)
                                .eqIfPresent(CmsStandardDO::getAuditStatus, reqVO.getAuditStatus())
                                .eqIfPresent(CmsStandardDO::getPublishStatus, reqVO.getPublishStatus())
                                .orderByDesc(CmsStandardDO::getStandardId));
                records = new ArrayList<>();
                for (CmsStandardDO d : standardPage.getList()) {
                    records.add(build(d.getStandardId(), "standard", d.getStandardName(),
                            d.getAuditStatus(), d.getPublishStatus(), d.getCreateTime()));
                }
                return success(new PageResult<>(records, standardPage.getTotal()));
            case "material":
                PageResult<CmsMaterialDO> materialPage = materialMapper.selectPage(reqVO,
                        new LambdaQueryWrapperX<CmsMaterialDO>()
                                .likeIfPresent(CmsMaterialDO::getMaterialName, keyword)
                                .eqIfPresent(CmsMaterialDO::getAuditStatus, reqVO.getAuditStatus())
                                .eqIfPresent(CmsMaterialDO::getPublishStatus, reqVO.getPublishStatus())
                                .orderByDesc(CmsMaterialDO::getMaterialId));
                records = new ArrayList<>();
                for (CmsMaterialDO d : materialPage.getList()) {
                    records.add(build(d.getMaterialId(), "material", d.getMaterialName(),
                            d.getAuditStatus(), d.getPublishStatus(), d.getCreateTime()));
                }
                return success(new PageResult<>(records, materialPage.getTotal()));
            case "process":
                PageResult<CmsProcessResourceDO> processPage = processMapper.selectPage(reqVO,
                        new LambdaQueryWrapperX<CmsProcessResourceDO>()
                                .likeIfPresent(CmsProcessResourceDO::getTitle, keyword)
                                .eqIfPresent(CmsProcessResourceDO::getAuditStatus, reqVO.getAuditStatus())
                                .eqIfPresent(CmsProcessResourceDO::getPublishStatus, reqVO.getPublishStatus())
                                .orderByDesc(CmsProcessResourceDO::getResourceId));
                records = new ArrayList<>();
                for (CmsProcessResourceDO d : processPage.getList()) {
                    records.add(build(d.getResourceId(), "process", d.getTitle(),
                            d.getAuditStatus(), d.getPublishStatus(), d.getCreateTime()));
                }
                return success(new PageResult<>(records, processPage.getTotal()));
            case "quota":
                PageResult<CmsQuotaDO> quotaPage = quotaMapper.selectPage(reqVO,
                        new LambdaQueryWrapperX<CmsQuotaDO>()
                                .likeIfPresent(CmsQuotaDO::getTitle, keyword)
                                .eqIfPresent(CmsQuotaDO::getAuditStatus, reqVO.getAuditStatus())
                                .eqIfPresent(CmsQuotaDO::getPublishStatus, reqVO.getPublishStatus())
                                .orderByDesc(CmsQuotaDO::getQuotaId));
                records = new ArrayList<>();
                for (CmsQuotaDO d : quotaPage.getList()) {
                    records.add(build(d.getQuotaId(), "quota", d.getTitle(),
                            d.getAuditStatus(), d.getPublishStatus(), d.getCreateTime()));
                }
                return success(new PageResult<>(records, quotaPage.getTotal()));
            case "policy":
                PageResult<CmsPolicyDO> policyPage = policyMapper.selectPage(reqVO,
                        new LambdaQueryWrapperX<CmsPolicyDO>()
                                .likeIfPresent(CmsPolicyDO::getTitle, keyword)
                                .eqIfPresent(CmsPolicyDO::getAuditStatus, reqVO.getAuditStatus())
                                .eqIfPresent(CmsPolicyDO::getPublishStatus, reqVO.getPublishStatus())
                                .orderByDesc(CmsPolicyDO::getPolicyId));
                records = new ArrayList<>();
                for (CmsPolicyDO d : policyPage.getList()) {
                    records.add(build(d.getPolicyId(), "policy", d.getTitle(),
                            d.getAuditStatus(), d.getPublishStatus(), d.getCreateTime()));
                }
                return success(new PageResult<>(records, policyPage.getTotal()));
            default:
                return success(PageResult.empty());
        }
    }

    private CmsSearchRespVO build(Long id, String type, String title,
                                  Integer auditStatus, Integer publishStatus, LocalDateTime createTime) {
        CmsSearchRespVO vo = new CmsSearchRespVO();
        vo.setId(id);
        vo.setType(type);
        vo.setTitle(title);
        vo.setAuditStatus(auditStatus);
        vo.setPublishStatus(publishStatus);
        vo.setCreateTime(createTime);
        return vo;
    }

}
