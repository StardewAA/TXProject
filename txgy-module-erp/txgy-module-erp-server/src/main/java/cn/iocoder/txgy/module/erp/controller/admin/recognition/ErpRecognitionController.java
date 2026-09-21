package cn.iocoder.txgy.module.erp.controller.admin.recognition;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionResultCorrectReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskActionReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskCreateReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpRecognitionTaskRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpPurchaseOrderImportExcelVO;
import cn.iocoder.txgy.module.erp.controller.admin.recognition.vo.ErpSaleOrderImportExcelVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionResultDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.recognition.ErpRecognitionTaskDO;
import cn.iocoder.txgy.module.erp.service.recognition.ErpRecognitionService;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionDocument;
import cn.iocoder.txgy.module.erp.service.recognition.dto.ErpRecognitionValidation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;

@Tag(name = "管理后台 - ERP 图片识别")
@RestController
@RequestMapping("/erp/recognition")
@Validated
public class ErpRecognitionController {

    @Resource
    private ErpRecognitionService recognitionService;

    @PostMapping("/task/create")
    @Operation(summary = "创建图片识别任务")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:create', 'erp:sale-order:create', 'erp:supplier:create')")
    public CommonResult<Long> createTask(@Valid @RequestBody ErpRecognitionTaskCreateReqVO createReqVO) {
        return success(recognitionService.createTask(createReqVO));
    }

    @PostMapping("/task/import-excel")
    @Operation(summary = "导入采购或销售订单 Excel")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:create', 'erp:sale-order:create', 'erp:supplier:create')")
    public CommonResult<Long> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceFileUrl") String sourceFileUrl,
            @RequestParam(value = "bizType", defaultValue = "PURCHASE_ORDER") String bizType) throws Exception {
        return success(recognitionService.importExcel(file, sourceFileUrl, bizType));
    }

    @GetMapping("/template/purchase-order")
    @Operation(summary = "下载采购订单导入模板")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:create', 'erp:sale-order:create')")
    public void downloadPurchaseOrderTemplate(HttpServletResponse response) throws IOException {
        ErpPurchaseOrderImportExcelVO sample = new ErpPurchaseOrderImportExcelVO();
        sample.setSupplierName("示例供应商（请替换）");
        sample.setDocumentDate("2026-09-08");
        sample.setDocumentNo("示例外部单号（请替换）");
        sample.setAccountName("结算账户名称（可选）");
        sample.setProductCode("产品编码或条码");
        sample.setProductName("产品名称（二选一）");
        sample.setCount(BigDecimal.ONE);
        sample.setProductPrice(BigDecimal.ZERO);
        sample.setTaxPercent("0");
        sample.setRemark("请删除本示例行后再导入");
        ExcelUtils.write(response, "采购订单导入模板.xls", "采购订单", ErpPurchaseOrderImportExcelVO.class,
                Collections.singletonList(sample));
    }

    @GetMapping("/template/sale-order")
    @Operation(summary = "下载销售订单导入模板")
    @PreAuthorize("@ss.hasAnyPermissions('erp:sale-order:create')")
    public void downloadSaleOrderTemplate(HttpServletResponse response) throws IOException {
        ErpSaleOrderImportExcelVO sample = new ErpSaleOrderImportExcelVO();
        sample.setCustomerName("示例客户（请替换）");
        sample.setDocumentDate("2026-09-09");
        sample.setDocumentNo("示例外部单号（请替换）");
        sample.setSaleUserName("销售员昵称（可选）");
        sample.setAccountName("结算账户名称（可选）");
        sample.setDiscountPercent(BigDecimal.ZERO);
        sample.setProductCode("产品编码或条码");
        sample.setProductName("产品名称（二选一）");
        sample.setCount(BigDecimal.ONE);
        sample.setProductPrice(BigDecimal.ZERO);
        sample.setTaxPercent("0");
        sample.setRemark("请删除本示例行后再导入");
        ExcelUtils.write(response, "销售订单导入模板.xls", "销售订单", ErpSaleOrderImportExcelVO.class,
                Collections.singletonList(sample));
    }

    @PostMapping("/task/retry")
    @Operation(summary = "重试图片识别任务")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:create', 'erp:sale-order:create', 'erp:supplier:create')")
    public CommonResult<Boolean> retryTask(@RequestParam("taskId") Long taskId) {
        recognitionService.retryTask(taskId);
        return success(true);
    }

    @DeleteMapping("/task/delete")
    @Operation(summary = "删除识别任务（不影响已导入业务单据）")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:create', 'erp:sale-order:create', 'erp:supplier:create')")
    public CommonResult<Boolean> deleteTask(@RequestParam("taskId") Long taskId) {
        recognitionService.deleteTask(taskId);
        return success(true);
    }

    @GetMapping("/task/get")
    @Operation(summary = "获得图片识别任务详情")
    @Parameter(name = "taskId", required = true)
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:query', 'erp:sale-order:query', 'erp:supplier:query')")
    public CommonResult<ErpRecognitionTaskRespVO> getTask(@RequestParam("taskId") Long taskId) {
        ErpRecognitionTaskDO task = recognitionService.getTask(taskId);
        return success(buildTaskRespVO(task));
    }

    @GetMapping("/task/page")
    @Operation(summary = "获得图片识别任务分页")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:query', 'erp:sale-order:query', 'erp:supplier:query')")
    public CommonResult<PageResult<ErpRecognitionTaskRespVO>> getTaskPage(@Valid ErpRecognitionTaskPageReqVO pageReqVO) {
        PageResult<ErpRecognitionTaskDO> pageResult = recognitionService.getTaskPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ErpRecognitionTaskRespVO.class));
    }

    @PutMapping("/result/correct")
    @Operation(summary = "保存识别结果的人工修正")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:update', 'erp:sale-order:update', 'erp:supplier:update')")
    public CommonResult<Boolean> correctResult(@Valid @RequestBody ErpRecognitionResultCorrectReqVO correctReqVO) {
        recognitionService.correctResult(correctReqVO);
        return success(true);
    }

    @PostMapping("/task/confirm")
    @Operation(summary = "确认识别结果")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:update', 'erp:sale-order:update', 'erp:supplier:update')")
    public CommonResult<Boolean> confirmTask(@Valid @RequestBody ErpRecognitionTaskActionReqVO actionReqVO) {
        recognitionService.confirmTask(actionReqVO.getTaskId(), actionReqVO.getResultVersion());
        return success(true);
    }

    @PostMapping("/task/import")
    @Operation(summary = "按识别业务类型生成 ERP 草稿")
    @PreAuthorize("@ss.hasAnyPermissions('erp:purchase-order:create', 'erp:sale-order:create', 'erp:supplier:create')")
    public CommonResult<Long> importRecognitionTask(@Valid @RequestBody ErpRecognitionTaskActionReqVO actionReqVO) {
        return success(recognitionService.importRecognitionTask(actionReqVO.getTaskId(), actionReqVO.getResultVersion()));
    }

    private ErpRecognitionTaskRespVO buildTaskRespVO(ErpRecognitionTaskDO task) {
        if (task == null) {
            return null;
        }
        ErpRecognitionTaskRespVO respVO = BeanUtils.toBean(task, ErpRecognitionTaskRespVO.class);
        ErpRecognitionResultDO result = recognitionService.getResultByTaskId(task.getId());
        if (result != null) {
            respVO.setDocument(JsonUtils.parseObject(result.getNormalizedJson(), ErpRecognitionDocument.class));
            respVO.setValidation(JsonUtils.parseObject(result.getValidationJson(), ErpRecognitionValidation.class));
        }
        return respVO;
    }

}
