package cn.iocoder.txgy.module.training.controller.admin.learning;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.controller.admin.learning.vo.ConfigUpdateReqVO;
import cn.iocoder.txgy.module.training.service.learning.LearningConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 学员学习中心-全局配置")
@RestController
@RequestMapping("/training/learning/config")
@Validated
public class LearningConfigController {

    @Resource
    private LearningConfigService learningConfigService;

    @GetMapping("/list")
    @Operation(summary = "获得学习全局配置（含默认值）")
    @PreAuthorize("@ss.hasPermission('training:learning-config:query')")
    public CommonResult<Map<String, String>> getConfigList() {
        return success(learningConfigService.getConfigMap());
    }

    @PutMapping("/update")
    @Operation(summary = "更新学习全局配置")
    @PreAuthorize("@ss.hasPermission('training:learning-config:update')")
    public CommonResult<Boolean> updateConfig(@Valid @RequestBody ConfigUpdateReqVO reqVO) {
        learningConfigService.updateConfig(reqVO.getKey(), reqVO.getValue());
        return success(true);
    }

}
