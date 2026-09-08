package cn.iocoder.txgy.module.hrm.controller.admin.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.resume.HrmRecruitResumeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.resume.HrmRecruitResumeDO;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 招聘简历版本")
@RestController
@RequestMapping("/hrm/recruit/resume")
@Validated
public class HrmRecruitResumeController {

    @Resource
    private HrmRecruitResumeService recruitResumeService;

    @PostMapping("/create")
    @Operation(summary = "上传招聘候选人简历版本")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:candidate:update')")
    public CommonResult<Long> createRecruitResume(@Valid @RequestBody HrmRecruitResumeSaveReqVO reqVO) {
        return success(recruitResumeService.createRecruitResume(reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "查询候选人简历版本")
    @Parameter(name = "candidateId", description = "候选人编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:recruit:candidate:query')")
    public CommonResult<List<HrmRecruitResumeRespVO>> getRecruitResumeList(
            @RequestParam("candidateId") Long candidateId) {
        return success(BeanUtils.toBean(recruitResumeService.getRecruitResumeList(candidateId),
                HrmRecruitResumeRespVO.class, vo -> vo.setFileUrl(null)));
    }

    @GetMapping("/preview")
    @Operation(summary = "获得简历预览授权地址")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:candidate:query')")
    public CommonResult<HrmRecruitResumeRespVO> previewRecruitResume(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(recruitResumeService.getAuthorizedRecruitResume(id),
                HrmRecruitResumeRespVO.class));
    }

    @GetMapping("/download")
    @Operation(summary = "获得简历下载授权地址")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:candidate:query')")
    public CommonResult<HrmRecruitResumeRespVO> downloadRecruitResume(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(recruitResumeService.getAuthorizedRecruitResume(id),
                HrmRecruitResumeRespVO.class));
    }

    @GetMapping("/legacy-preview")
    @Operation(summary = "获得历史简历附件预览授权地址")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:candidate:query')")
    public CommonResult<HrmRecruitResumeRespVO> previewLegacyRecruitResume(
            @RequestParam("candidateId") Long candidateId, @RequestParam("index") Integer index) {
        return success(BeanUtils.toBean(recruitResumeService.getAuthorizedLegacyRecruitResume(candidateId, index),
                HrmRecruitResumeRespVO.class));
    }

    @GetMapping("/legacy-download")
    @Operation(summary = "获得历史简历附件下载授权地址")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:candidate:query')")
    public CommonResult<HrmRecruitResumeRespVO> downloadLegacyRecruitResume(
            @RequestParam("candidateId") Long candidateId, @RequestParam("index") Integer index) {
        return success(BeanUtils.toBean(recruitResumeService.getAuthorizedLegacyRecruitResume(candidateId, index),
                HrmRecruitResumeRespVO.class));
    }
}
