package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit;

import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.hrm.service.recruit.file.HrmRecruitLocalFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "员工端 - HRM 招聘文件")
@RestController
@RequestMapping("/hrm/portal/recruit/file")
@Validated
public class HrmPortalRecruitFileController {

    @Resource
    private HrmRecruitLocalFileService localFileService;

    @PostMapping("/upload")
    @Operation(summary = "上传招聘简历或沟通图片")
    @PreAuthorize("isAuthenticated()")
    public cn.iocoder.txgy.framework.common.pojo.CommonResult<String> upload(
            @RequestParam("file") MultipartFile file, @RequestParam("category") String category) throws Exception {
        return cn.iocoder.txgy.framework.common.pojo.CommonResult.success(localFileService.save(file, category));
    }

    @GetMapping("/{fileName:.+}")
    @Operation(summary = "读取招聘简历或沟通图片")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<org.springframework.core.io.Resource> getFile(@PathVariable String fileName) {
        org.springframework.core.io.Resource resource = localFileService.load(fileName);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}
