package cn.iocoder.txgy.module.training.controller.admin.basedata;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapRespVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapSaveReqVO;
import cn.iocoder.txgy.module.training.controller.admin.basedata.vo.LearningMapStageVO;
import cn.iocoder.txgy.module.training.dal.dataobject.basedata.LearningMapDO;
import cn.iocoder.txgy.module.training.service.basedata.LearningMapService;
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
 * 学习地图（按成长阶段组织的课程路径）
 *
 * @author 云培训学院课题小组
 */
@Tag(name = "管理后台 - 学习地图")
@RestController
@RequestMapping("/training/learning-map")
@Validated
public class LearningMapController {

    @Resource
    private LearningMapService learningMapService;

    @PostMapping("/create")
    @Operation(summary = "创建学习地图记录")
    @PreAuthorize("@ss.hasPermission('training:learning-map:create')")
    public CommonResult<Long> createLearningMap(@Valid @RequestBody LearningMapSaveReqVO createReqVO) {
        return success(learningMapService.createLearningMap(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新学习地图记录")
    @PreAuthorize("@ss.hasPermission('training:learning-map:update')")
    public CommonResult<Boolean> updateLearningMap(@Valid @RequestBody LearningMapSaveReqVO updateReqVO) {
        learningMapService.updateLearningMap(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除学习地图记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:learning-map:delete')")
    public CommonResult<Boolean> deleteLearningMap(@RequestParam("id") Long id) {
        learningMapService.deleteLearningMap(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得学习地图记录")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:learning-map:query')")
    public CommonResult<LearningMapRespVO> getLearningMap(@RequestParam("id") Long id) {
        LearningMapDO map = learningMapService.getLearningMap(id);
        return success(BeanUtils.toBean(map, LearningMapRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得学习地图分页")
    @PreAuthorize("@ss.hasPermission('training:learning-map:query')")
    public CommonResult<PageResult<LearningMapRespVO>> getLearningMapPage(@Valid LearningMapPageReqVO pageReqVO) {
        PageResult<LearningMapDO> pageResult = learningMapService.getLearningMapPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, LearningMapRespVO.class));
    }

    @GetMapping("/list-by-job")
    @Operation(summary = "按工种获得学习地图课程清单")
    @Parameter(name = "jobCode", description = "工种编码", required = true, example = "OP-02")
    @PreAuthorize("@ss.hasPermission('training:learning-map:query')")
    public CommonResult<List<LearningMapRespVO>> getLearningMapListByJob(@RequestParam("jobCode") String jobCode) {
        return success(BeanUtils.toBean(learningMapService.getLearningMapListByJobCode(jobCode),
                LearningMapRespVO.class));
    }

    @GetMapping("/stages")
    @Operation(summary = "按工种获得学习地图四阶段分组视图（入门段/核心段/进阶段/技师段）")
    @Parameter(name = "jobCode", description = "工种编码", required = true, example = "OP-02")
    @PreAuthorize("@ss.hasPermission('training:learning-map:query')")
    public CommonResult<List<LearningMapStageVO>> getLearningMapStages(@RequestParam("jobCode") String jobCode) {
        return success(learningMapService.getLearningMapStagesByJobCode(jobCode));
    }

}
