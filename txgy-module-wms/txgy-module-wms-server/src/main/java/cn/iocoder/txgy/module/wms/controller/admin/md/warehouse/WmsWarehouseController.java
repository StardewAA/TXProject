package cn.iocoder.txgy.module.wms.controller.admin.md.warehouse;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo.WmsWarehousePageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo.WmsWarehouseRespVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo.WmsWarehouseSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.hutool.core.util.NumberUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - WMS 仓库")
@RestController
@RequestMapping("/wms/warehouse")
@Validated
public class WmsWarehouseController {

    @Resource
    private WmsWarehouseService warehouseService;

    @Resource
    private AdminUserApi adminUserApi;

    @PostMapping("/create")
    @Operation(summary = "创建仓库")
    @PreAuthorize("@ss.hasPermission('wms:warehouse:create')")
    public CommonResult<Long> createWarehouse(@Valid @RequestBody WmsWarehouseSaveReqVO createReqVO) {
        return success(warehouseService.createWarehouse(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新仓库")
    @PreAuthorize("@ss.hasPermission('wms:warehouse:update')")
    public CommonResult<Boolean> updateWarehouse(@Valid @RequestBody WmsWarehouseSaveReqVO updateReqVO) {
        warehouseService.updateWarehouse(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除仓库")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('wms:warehouse:delete')")
    public CommonResult<Boolean> deleteWarehouse(@RequestParam("id") Long id) {
        warehouseService.deleteWarehouse(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得仓库")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:warehouse:query')")
    public CommonResult<WmsWarehouseRespVO> getWarehouse(@RequestParam("id") Long id) {
        WmsWarehouseDO warehouse = warehouseService.getWarehouse(id);
        return success(buildWarehouseRespVO(warehouse));
    }

    @GetMapping("/page")
    @Operation(summary = "获得仓库分页")
    @PreAuthorize("@ss.hasPermission('wms:warehouse:query')")
    public CommonResult<PageResult<WmsWarehouseRespVO>> getWarehousePage(@Valid WmsWarehousePageReqVO pageReqVO) {
        PageResult<WmsWarehouseDO> pageResult = warehouseService.getWarehousePage(pageReqVO);
        List<WmsWarehouseDO> list = pageResult.getList();
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(list.stream()
                .flatMap(warehouse -> java.util.stream.Stream.of(parseUserId(warehouse.getCreator()), parseUserId(warehouse.getUpdater())))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        return success(new PageResult<>(list.stream().map(warehouse -> buildWarehouseRespVO(warehouse, userMap)).toList(), pageResult.getTotal()));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得仓库精简列表", description = "主要用于前端下拉")
    @PreAuthorize("@ss.hasPermission('wms:warehouse:query')")
    public CommonResult<List<WmsWarehouseRespVO>> getWarehouseSimpleList() {
        List<WmsWarehouseDO> list = warehouseService.getWarehouseList();
        return success(BeanUtils.toBean(list, WmsWarehouseRespVO.class));
    }

    private WmsWarehouseRespVO buildWarehouseRespVO(WmsWarehouseDO warehouse) {
        if (warehouse == null) return null;
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(java.util.stream.Stream.of(
                parseUserId(warehouse.getCreator()), parseUserId(warehouse.getUpdater()))
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        return buildWarehouseRespVO(warehouse, userMap);
    }

    private WmsWarehouseRespVO buildWarehouseRespVO(WmsWarehouseDO warehouse, Map<Long, AdminUserRespDTO> userMap) {
        WmsWarehouseRespVO vo = BeanUtils.toBean(warehouse, WmsWarehouseRespVO.class);
        MapUtils.findAndThen(userMap, parseUserId(vo.getCreator()), user -> vo.setCreatorName(user.getNickname()));
        MapUtils.findAndThen(userMap, parseUserId(vo.getUpdater()), user -> vo.setUpdaterName(user.getNickname()));
        return vo;
    }

    private Long parseUserId(String userId) {
        return NumberUtil.parseLong(userId, null);
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出仓库 Excel")
    @PreAuthorize("@ss.hasPermission('wms:warehouse:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportWarehouseExcel(@Valid WmsWarehousePageReqVO pageReqVO,
                                     HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<WmsWarehouseDO> list = warehouseService.getWarehousePage(pageReqVO).getList();
        ExcelUtils.write(response, "仓库.xls", "数据", WmsWarehouseRespVO.class,
                BeanUtils.toBean(list, WmsWarehouseRespVO.class));
    }

}
