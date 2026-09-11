package cn.iocoder.txgy.module.training.controller.admin.basedata;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobCertSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobCertDO;
import cn.iocoder.txgy.module.training.service.basedata.JobCertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 工种资格证（持证上岗校验依据）
 *
 * @author 云培训学院课题小组
 */
@Tag(name = "管理后台 - 工种资格证")
@RestController
@RequestMapping("/training/job-cert")
@Validated
public class JobCertController {

    @Resource
    private JobCertService jobCertService;

    @PostMapping("/create")
    @Operation(summary = "创建工种资格证")
    @PreAuthorize("@ss.hasPermission('training:job-cert:create')")
    public CommonResult<Long> createJobCert(@Valid @RequestBody JobCertSaveReqVO createReqVO) {
        return success(jobCertService.createJobCert(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工种资格证")
    @PreAuthorize("@ss.hasPermission('training:job-cert:update')")
    public CommonResult<Boolean> updateJobCert(@Valid @RequestBody JobCertSaveReqVO updateReqVO) {
        jobCertService.updateJobCert(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工种资格证")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:job-cert:delete')")
    public CommonResult<Boolean> deleteJobCert(@RequestParam("id") Long id) {
        jobCertService.deleteJobCert(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工种资格证")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:job-cert:query')")
    public CommonResult<JobCertRespVO> getJobCert(@RequestParam("id") Long id) {
        JobCertDO cert = jobCertService.getJobCert(id);
        return success(BeanUtils.toBean(cert, JobCertRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得工种资格证分页")
    @PreAuthorize("@ss.hasPermission('training:job-cert:query')")
    public CommonResult<PageResult<JobCertRespVO>> getJobCertPage(@Valid JobCertPageReqVO pageReqVO) {
        PageResult<JobCertDO> pageResult = jobCertService.getJobCertPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, JobCertRespVO.class));
    }

    @GetMapping("/list-by-job")
    @Operation(summary = "按工种获得资格证要求清单")
    @Parameter(name = "jobCode", description = "工种编码", required = true, example = "OP-02")
    @PreAuthorize("@ss.hasPermission('training:job-cert:query')")
    public CommonResult<List<JobCertRespVO>> getJobCertListByJob(@RequestParam("jobCode") String jobCode) {
        return success(BeanUtils.toBean(jobCertService.getJobCertListByJobCode(jobCode), JobCertRespVO.class));
    }

}
