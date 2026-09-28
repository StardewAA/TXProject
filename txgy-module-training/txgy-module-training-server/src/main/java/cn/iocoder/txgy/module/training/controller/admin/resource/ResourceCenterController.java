package cn.iocoder.txgy.module.training.controller.admin.resource;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.training.dal.mysql.resource.DocumentResourceMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.QuestionMapper;
import cn.iocoder.txgy.module.training.dal.mysql.resource.VideoResourceMapper;
import cn.iocoder.txgy.module.training.enums.resource.ResourceStatusEnum;
import cn.iocoder.txgy.module.training.enums.resource.TranscodeStatusEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 资源中心工作台统计")
@RestController
@RequestMapping("/training/resource-center")
@Validated
public class ResourceCenterController {

    @Resource
    private DocumentResourceMapper documentMapper;
    @Resource
    private VideoResourceMapper videoMapper;
    @Resource
    private QuestionMapper questionMapper;

    @GetMapping("/stats")
    @Operation(summary = "资源中心统计（文档/视频/试题规模与关键状态）")
    @PreAuthorize("@ss.hasPermission('training:resource:query')")
    public CommonResult<Map<String, Object>> getResourceCenterStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 文档库
        stats.put("documentTotal", documentMapper.selectCount(null));
        stats.put("documentOnline", documentMapper.selectCountByStatus(ResourceStatusEnum.ONLINE.getStatus()));
        stats.put("documentOffline", documentMapper.selectCountByStatus(ResourceStatusEnum.OFFLINE.getStatus()));
        stats.put("documentArchived", documentMapper.selectCountByStatus(ResourceStatusEnum.ARCHIVED.getStatus()));
        // 视频库
        stats.put("videoTotal", videoMapper.selectCount(null));
        stats.put("videoOnline", videoMapper.selectCountByStatus(ResourceStatusEnum.ONLINE.getStatus()));
        stats.put("videoTranscoded", videoMapper.selectCount(
                new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<
                        cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO>()
                        .eq(cn.iocoder.txgy.module.training.dal.dataobject.resource.VideoResourceDO::getTranscodeStatus,
                                TranscodeStatusEnum.SUCCESS.getStatus())));
        // 题库
        stats.put("questionTotal", questionMapper.selectCount(null));
        stats.put("questionEnabled", questionMapper.selectCountByStatus(1));
        stats.put("questionSingle", questionMapper.selectCountByQtype(1));
        stats.put("questionMultiple", questionMapper.selectCountByQtype(2));
        stats.put("questionJudge", questionMapper.selectCountByQtype(3));
        stats.put("questionQa", questionMapper.selectCountByQtype(4));
        stats.put("questionCase", questionMapper.selectCountByQtype(5));
        return success(stats);
    }

}
