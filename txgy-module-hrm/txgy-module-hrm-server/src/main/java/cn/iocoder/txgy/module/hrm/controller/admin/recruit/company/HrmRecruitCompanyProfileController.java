package cn.iocoder.txgy.module.hrm.controller.admin.recruit.company;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company.HrmRecruitCompanyProfileRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company.HrmRecruitCompanyProfileSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.company.HrmRecruitCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.service.recruit.company.HrmRecruitCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.recruit.company.HrmRecruitCompanyMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 招聘企业信息")
@RestController
@RequestMapping("/hrm/recruit/company")
@Validated
public class HrmRecruitCompanyProfileController {

    @Resource
    private HrmRecruitCompanyProfileService companyProfileService;

    @Resource
    private HrmRecruitCompanyMediaService companyMediaService;

    @GetMapping("/get")
    @Operation(summary = "查询当前租户招聘企业信息")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:company:query')")
    public CommonResult<HrmRecruitCompanyProfileRespVO> get() {
        HrmRecruitCompanyProfileDO profile = companyProfileService.getCurrentTenantProfile();
        return success(BeanUtils.toBean(profile, HrmRecruitCompanyProfileRespVO.class));
    }

    @PostMapping("/save")
    @Operation(summary = "保存当前租户招聘企业信息")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:company:update')")
    public CommonResult<Long> save(@Valid @RequestBody HrmRecruitCompanyProfileSaveReqVO reqVO) {
        return success(companyProfileService.saveCurrentTenantProfile(reqVO));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传招聘企业图片")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:company:update')")
    public CommonResult<String> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam("category") String category) throws Exception {
        return success(companyMediaService.save(file, category));
    }

    @GetMapping("/file/{fileName:.+}")
    @Operation(summary = "读取招聘企业图片")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<org.springframework.core.io.Resource> getFile(@PathVariable String fileName) {
        org.springframework.core.io.Resource resource = companyMediaService.load(fileName);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}
