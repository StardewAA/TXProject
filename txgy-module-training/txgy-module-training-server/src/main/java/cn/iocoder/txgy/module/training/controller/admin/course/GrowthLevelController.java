package cn.iocoder.txgy.module.training.controller.admin.course;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.GrowthLevelPageReqVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.GrowthLevelRespVO;
import cn.iocoder.txgy.module.training.controller.admin.course.vo.GrowthLevelSaveReqVO;
import cn.iocoder.txgy.module.training.dal.dataobject.course.GrowthLevelDO;
import cn.iocoder.txgy.module.training.service.course.GrowthLevelService;
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

@Tag(name = "管理后台 - 成长等级")
@RestController
@RequestMapping("/training/growth-level")
@Validated
public class GrowthLevelController {

    @Resource
    private GrowthLevelService growthLevelService;

    @PostMapping("/create")
    @Operation(summary = "新增成长等级")
    @PreAuthorize("@ss.hasPermission('training:course:create')")
    public CommonResult<Long> createGrowthLevel(@Valid @RequestBody GrowthLevelSaveReqVO createReqVO) {
        return success(growthLevelService.createGrowthLevel(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改成长等级")
    @PreAuthorize("@ss.hasPermission('training:course:update')")
    public CommonResult<Boolean> updateGrowthLevel(@Valid @RequestBody GrowthLevelSaveReqVO updateReqVO) {
        growthLevelService.updateGrowthLevel(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除成长等级（已被课程标记引用时返回 409 拦截）")
    @Parameter(name = "id", description = "等级 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:delete')")
    public CommonResult<Boolean> deleteGrowthLevel(@RequestParam("id") Long id) {
        growthLevelService.deleteGrowthLevel(id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "成长等级分页")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<PageResult<GrowthLevelRespVO>> getGrowthLevelPage(@Validated GrowthLevelPageReqVO pageReqVO) {
        PageResult<GrowthLevelDO> pageResult = growthLevelService.getGrowthLevelPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, GrowthLevelRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "成长等级全量列表（下拉选择：课程等级标记/人才成长统计）")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<List<GrowthLevelRespVO>> getGrowthLevelList() {
        List<GrowthLevelDO> list = growthLevelService.getGrowthLevelList();
        return success(BeanUtils.toBean(list, GrowthLevelRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "成长等级详情")
    @Parameter(name = "id", description = "等级 ID", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('training:course:query')")
    public CommonResult<GrowthLevelRespVO> getGrowthLevel(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(growthLevelService.getGrowthLevel(id), GrowthLevelRespVO.class));
    }

}
