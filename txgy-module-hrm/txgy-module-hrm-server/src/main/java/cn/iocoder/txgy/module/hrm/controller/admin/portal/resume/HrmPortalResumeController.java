package cn.iocoder.txgy.module.hrm.controller.admin.portal.resume;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeCreateReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.resume.vo.HrmPortalResumeUploadReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeDO;
import cn.iocoder.txgy.module.hrm.service.personal.resume.HrmPersonalResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "员工端 - 我的简历")
@RestController
@RequestMapping("/hrm/portal/resume")
@TenantIgnore
@Validated
public class HrmPortalResumeController {

    @Resource
    private HrmPersonalResumeService resumeService;

    @PostMapping("/upload")
    @Operation(summary = "上传我的简历附件")
    public CommonResult<Long> uploadResume(@Valid @RequestBody HrmPortalResumeUploadReqVO reqVO) {
        return success(resumeService.createUploadResume(getLoginUserId(), reqVO));
    }

    @PostMapping("/upload-generated")
    @Operation(summary = "保存附件简历制作生成的简历")
    public CommonResult<Long> uploadGeneratedResume(@Valid @RequestBody HrmPortalResumeUploadReqVO reqVO) {
        return success(resumeService.createGeneratedResume(getLoginUserId(), reqVO));
    }

    @PostMapping("/create")
    @Operation(summary = "在线创建我的简历")
    public CommonResult<Long> createResume(@Valid @RequestBody HrmPortalResumeCreateReqVO reqVO) {
        return success(resumeService.createOnlineResume(getLoginUserId(), reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "查询我的简历列表")
    public CommonResult<List<HrmPortalResumeRespVO>> getResumeList() {
        List<HrmPersonalResumeDO> list = resumeService.getResumeList(getLoginUserId());
        return success(BeanUtils.toBean(list, HrmPortalResumeRespVO.class));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除我的简历附件")
    public CommonResult<Boolean> deleteResume(@RequestParam("id") Long id) {
        return success(resumeService.deleteUploadResume(getLoginUserId(), id));
    }
}
