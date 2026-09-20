package cn.iocoder.txgy.module.hrm.controller.admin.portal.resume;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeVersionCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeVersionRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 员工端 - 我的简历版本。
 *
 * <p>所有归属判断都以登录用户为准，不接受前端传入的用户编号。</p>
 */
@Tag(name = "员工端 - 我的简历版本")
@RestController
@RequestMapping("/hrm/portal/resume-version")
@TenantIgnore
@Validated
public class HrmPortalResumeVersionController {

    @Resource
    private HrmPersonalResumeVersionService versionService;

    @PostMapping("/create")
    @Operation(summary = "把在线简历固化为新版本")
    public CommonResult<Long> createVersion(@Valid @RequestBody HrmPortalResumeVersionCreateReqVO reqVO) {
        return success(versionService.createVersion(getLoginUserId(), reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "查询我的简历版本列表")
    public CommonResult<List<HrmPortalResumeVersionRespVO>> getVersionList(
            @Parameter(description = "来源个人简历编号") @RequestParam(value = "sourceResumeId", required = false)
            Long sourceResumeId) {
        List<HrmPersonalResumeVersionDO> list = versionService.getVersionList(getLoginUserId(), sourceResumeId);
        // 列表不返回完整内容，避免响应体过大
        List<HrmPortalResumeVersionRespVO> result = BeanUtils.toBean(list, HrmPortalResumeVersionRespVO.class);
        result.forEach(item -> item.setContent(null));
        return success(result);
    }

    @GetMapping("/get")
    @Operation(summary = "查询我的简历版本详情")
    public CommonResult<HrmPortalResumeVersionRespVO> getVersion(
            @Parameter(description = "版本编号") @RequestParam("id") Long id) {
        HrmPersonalResumeVersionDO version = versionService.getVersion(getLoginUserId(), id);
        return success(BeanUtils.toBean(version, HrmPortalResumeVersionRespVO.class));
    }
}
