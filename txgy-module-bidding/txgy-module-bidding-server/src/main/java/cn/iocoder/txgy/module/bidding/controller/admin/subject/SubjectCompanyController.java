package cn.iocoder.txgy.module.bidding.controller.admin.subject;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectCompanySaveReqVO;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectCompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 所属企业")
@RestController
@RequestMapping("/bidding/subject-company")
@Validated
public class SubjectCompanyController {

    @Resource
    private SubjectCompanyService subjectCompanyService;

    @GetMapping("/page")
    @Operation(summary = "所属企业分页")
    @PreAuthorize("@ss.hasPermission('bidding:subject-company:query')")
    public CommonResult<PageResult<SubjectCompanyRespVO>> page(@Valid SubjectCompanyPageReqVO reqVO) {
        return success(subjectCompanyService.getSubjectCompanyPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "所属企业详情")
    @PreAuthorize("@ss.hasPermission('bidding:subject-company:query')")
    public CommonResult<SubjectCompanyRespVO> get(@RequestParam("id") Long id) {
        return success(subjectCompanyService.getSubjectCompany(id));
    }

    @PostMapping("/create")
    @Operation(summary = "新增所属企业")
    @PreAuthorize("@ss.hasPermission('bidding:subject-company:create')")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody SubjectCompanySaveReqVO reqVO) {
        return success(subjectCompanyService.createSubjectCompany(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改所属企业")
    @PreAuthorize("@ss.hasPermission('bidding:subject-company:update')")
    @Idempotent
    public CommonResult<Boolean> update(@Valid @RequestBody SubjectCompanySaveReqVO reqVO) {
        subjectCompanyService.updateSubjectCompany(reqVO);
        return success(true);
    }

    @PutMapping("/status")
    @Operation(summary = "启用/停用所属企业")
    @PreAuthorize("@ss.hasPermission('bidding:subject-company:update')")
    @Idempotent
    public CommonResult<Boolean> status(@RequestParam("id") Long id, @RequestParam("enabled") Boolean enabled) {
        subjectCompanyService.updateSubjectCompanyStatus(id, enabled);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除所属企业")
    @PreAuthorize("@ss.hasPermission('bidding:subject-company:delete')")
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        subjectCompanyService.deleteSubjectCompany(id);
        return success(true);
    }
}
