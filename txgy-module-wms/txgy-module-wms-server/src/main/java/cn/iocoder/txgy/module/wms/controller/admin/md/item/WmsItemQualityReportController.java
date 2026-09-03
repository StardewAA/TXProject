package cn.iocoder.txgy.module.wms.controller.admin.md.item;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.quality.WmsItemQualityReportRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.quality.WmsItemQualityReportSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemQualityReportDO;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemQualityReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - WMS 商品质检报告")
@RestController
@RequestMapping("/wms/item-quality-report")
@Validated
public class WmsItemQualityReportController {
    @Resource
    private WmsItemQualityReportService qualityReportService;
    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/create")
    @Operation(summary = "新增商品质检报告")
    @PreAuthorize("@ss.hasPermission('wms:item-quality-report:create')")
    public CommonResult<Long> create(@Valid @RequestBody WmsItemQualityReportSaveReqVO reqVO) {
        return success(qualityReportService.createQualityReport(reqVO));
    }

    @GetMapping("/list")
    @Operation(summary = "查询商品质检报告历史")
    @PreAuthorize("@ss.hasPermission('wms:item-quality-report:query')")
    public CommonResult<List<WmsItemQualityReportRespVO>> getList(@RequestParam("itemId") Long itemId) {
        List<WmsItemQualityReportDO> list = qualityReportService.getQualityReportList(itemId);
        WmsItemQualityReportDO current = qualityReportService.getCurrentQualityReport(itemId);
        return success(buildRespList(list, current == null ? null : current.getId()));
    }

    @GetMapping("/current")
    @Operation(summary = "查询商品当前质检报告")
    @PreAuthorize("@ss.hasPermission('wms:item-quality-report:query')")
    public CommonResult<WmsItemQualityReportRespVO> getCurrent(@RequestParam("itemId") Long itemId) {
        WmsItemQualityReportDO report = qualityReportService.getCurrentQualityReport(itemId);
        return success(report == null ? null : buildRespList(List.of(report), report.getId()).get(0));
    }

    @GetMapping("/current-batch")
    @Operation(summary = "批量查询商品当前质检报告")
    @PreAuthorize("@ss.hasPermission('wms:item-quality-report:query')")
    public CommonResult<List<WmsItemQualityReportRespVO>> getCurrentBatch(
            @RequestParam("itemIds") Collection<Long> itemIds) {
        Map<Long, WmsItemQualityReportDO> map = qualityReportService.getCurrentQualityReportMap(itemIds);
        return success(buildRespList(map.values(), null));
    }

    private List<WmsItemQualityReportRespVO> buildRespList(Collection<WmsItemQualityReportDO> reports,
                                                            Long currentId) {
        if (CollUtil.isEmpty(reports)) {
            return Collections.emptyList();
        }
        List<WmsItemQualityReportDO> reportList = new ArrayList<>(reports);
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(convertSet(reportList,
                report -> NumberUtil.parseLong(report.getCreator(), null)));
        return BeanUtils.toBean(reportList, WmsItemQualityReportRespVO.class, vo -> {
            vo.setImageUrls(JsonUtils.parseArray(reportList.stream().filter(report -> report.getId().equals(vo.getId()))
                    .findFirst().orElseThrow().getImageUrls(), String.class));
            vo.setCurrent(currentId == null || currentId.equals(vo.getId()));
            AdminUserRespDTO user = userMap.get(NumberUtil.parseLong(vo.getCreator(), null));
            if (user != null) {
                vo.setCreatorName(user.getNickname());
            }
        });
    }
}
