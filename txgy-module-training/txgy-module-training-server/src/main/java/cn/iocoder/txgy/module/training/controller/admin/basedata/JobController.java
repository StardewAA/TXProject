package cn.iocoder.txgy.module.training.controller.admin.basedata;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobDetailRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.JobSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.JobDO;
import cn.iocoder.txgy.module.training.service.basedata.JobService;
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
 * 岗位工种（云培训学院基础数据字典）
 *
 * @author 云培训学院课题小组
 */
@Tag(name = "管理后台 - 岗位工种")
@RestController
@RequestMapping("/training/job")
@Validated
public class JobController {

    @Resource
    private JobService jobService;

    @PostMapping("/create")
    @Operation(summary = "创建岗位工种")
    @PreAuthorize("@ss.hasPermission('training:job:create')")
    public CommonResult<Long> createJob(@Valid @RequestBody JobSaveReqVO createReqVO) {
        return success(jobService.createJob(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新岗位工种")
    @PreAuthorize("@ss.hasPermission('training:job:update')")
    public CommonResult<Boolean> updateJob(@Valid @RequestBody JobSaveReqVO updateReqVO) {
        jobService.updateJob(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除岗位工种（存在资格证或学习地图引用时拦截）")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:job:delete')")
    public CommonResult<Boolean> deleteJob(@RequestParam("id") Long id) {
        jobService.deleteJob(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得岗位工种")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:job:query')")
    public CommonResult<JobRespVO> getJob(@RequestParam("id") Long id) {
        JobDO job = jobService.getJob(id);
        return success(BeanUtils.toBean(job, JobRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得岗位工种分页")
    @PreAuthorize("@ss.hasPermission('training:job:query')")
    public CommonResult<PageResult<JobRespVO>> getJobPage(@Valid JobPageReqVO pageReqVO) {
        PageResult<JobDO> pageResult = jobService.getJobPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, JobRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得岗位工种全量列表（下拉选择用）")
    @PreAuthorize("@ss.hasPermission('training:job:query')")
    public CommonResult<List<JobRespVO>> getJobList() {
        return success(BeanUtils.toBean(jobService.getJobList(), JobRespVO.class));
    }

    @GetMapping("/detail")
    @Operation(summary = "获得岗位工种全景详情（含资格证与学习地图四阶段路径）")
    @Parameter(name = "code", description = "工种编码", required = true, example = "OP-02")
    @PreAuthorize("@ss.hasPermission('training:job:query')")
    public CommonResult<JobDetailRespVO> getJobDetail(@RequestParam("code") String code) {
        return success(jobService.getJobDetail(code));
    }

}
