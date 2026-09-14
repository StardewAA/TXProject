package cn.iocoder.txgy.module.crm.controller.admin.quotation;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.MapUtils;
import cn.iocoder.txgy.framework.common.util.number.NumberUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationPageReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationParseReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationParseRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationRespVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationSaveReqVO;
import cn.iocoder.txgy.module.crm.controller.admin.quotation.vo.quotation.CrmQuotationTransferReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.business.CrmBusinessDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.product.CrmProductDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.quotation.CrmQuotationDO;
import cn.iocoder.txgy.module.crm.dal.dataobject.quotation.CrmQuotationProductDO;
import cn.iocoder.txgy.module.crm.service.business.CrmBusinessService;
import cn.iocoder.txgy.module.crm.service.customer.CrmCustomerService;
import cn.iocoder.txgy.module.crm.service.product.CrmProductService;
import cn.iocoder.txgy.module.crm.service.quotation.CrmQuotationService;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static cn.iocoder.txgy.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.framework.common.util.collection.MapUtils.findAndThen;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static java.util.Collections.singletonList;

@Tag(name = "管理后台 - CRM 报价单")
@RestController
@RequestMapping("/crm/quotation")
@Validated
public class CrmQuotationController {

    @Resource
    private CrmQuotationService quotationService;
    @Resource
    private CrmCustomerService customerService;
    @Resource
    private CrmBusinessService businessService;
    @Resource
    private CrmProductService productService;

    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    @PostMapping("/create")
    @Operation(summary = "创建报价单")
    @PreAuthorize("@ss.hasPermission('crm:quotation:create')")
    public CommonResult<Long> createQuotation(@Valid @RequestBody CrmQuotationSaveReqVO createReqVO) {
        return success(quotationService.createQuotation(createReqVO, getLoginUserId()));
    }

    @PutMapping("/update")
    @Operation(summary = "更新报价单")
    @PreAuthorize("@ss.hasPermission('crm:quotation:update')")
    public CommonResult<Boolean> updateQuotation(@Valid @RequestBody CrmQuotationSaveReqVO updateReqVO) {
        quotationService.updateQuotation(updateReqVO, getLoginUserId());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除报价单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('crm:quotation:delete')")
    public CommonResult<Boolean> deleteQuotation(@RequestParam("id") Long id) {
        quotationService.deleteQuotation(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得报价单")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('crm:quotation:query')")
    public CommonResult<CrmQuotationRespVO> getQuotation(@RequestParam("id") Long id) {
        CrmQuotationDO quotation = quotationService.getQuotation(id);
        return success(buildQuotationDetail(quotation));
    }

    private CrmQuotationRespVO buildQuotationDetail(CrmQuotationDO quotation) {
        if (quotation == null) {
            return null;
        }
        CrmQuotationRespVO quotationVO = buildQuotationDetailList(singletonList(quotation)).get(0);
        // 拼接产品项
        List<CrmQuotationProductDO> quotationProducts = quotationService.getQuotationProductListByQuotationId(quotationVO.getId());
        Map<Long, CrmProductDO> productMap = productService.getProductMap(
                convertSet(quotationProducts, CrmQuotationProductDO::getProductId));
        quotationVO.setProducts(BeanUtils.toBean(quotationProducts, CrmQuotationRespVO.Product.class, quotationProductVO ->
                MapUtils.findAndThen(productMap, quotationProductVO.getProductId(),
                        product -> quotationProductVO.setProductName(product.getName())
                                .setProductNo(product.getNo()).setProductUnit(product.getUnit()))));
        return quotationVO;
    }

    @GetMapping("/page")
    @Operation(summary = "获得报价单分页")
    @PreAuthorize("@ss.hasPermission('crm:quotation:query')")
    public CommonResult<PageResult<CrmQuotationRespVO>> getQuotationPage(@Valid CrmQuotationPageReqVO pageVO) {
        PageResult<CrmQuotationDO> pageResult = quotationService.getQuotationPage(pageVO, getLoginUserId());
        return success(BeanUtils.toBean(pageResult, CrmQuotationRespVO.class).setList(buildQuotationDetailList(pageResult.getList())));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出报价单 Excel")
    @PreAuthorize("@ss.hasPermission('crm:quotation:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportQuotationExcel(@Valid CrmQuotationPageReqVO exportReqVO,
                                    HttpServletResponse response) throws IOException {
        PageResult<CrmQuotationDO> pageResult = quotationService.getQuotationPage(exportReqVO, getLoginUserId());
        // 导出 Excel
        ExcelUtils.write(response, "报价单.xls", "数据", CrmQuotationRespVO.class,
                BeanUtils.toBean(pageResult.getList(), CrmQuotationRespVO.class));
    }

    @PutMapping("/transfer")
    @Operation(summary = "报价单转移")
    @PreAuthorize("@ss.hasPermission('crm:quotation:update')")
    public CommonResult<Boolean> transferQuotation(@Valid @RequestBody CrmQuotationTransferReqVO reqVO) {
        quotationService.transferQuotation(reqVO, getLoginUserId());
        return success(true);
    }

    @PutMapping("/submit")
    @Operation(summary = "提交报价单审批")
    @PreAuthorize("@ss.hasPermission('crm:quotation:update')")
    public CommonResult<Boolean> submitQuotation(@RequestParam("id") Long id) {
        quotationService.submitQuotation(id, getLoginUserId());
        return success(true);
    }

    @PostMapping("/create-contract")
    @Operation(summary = "报价单转为合同", description = "由审批通过的报价单生成合同草稿")
    @PreAuthorize("@ss.hasPermission('crm:quotation:update')")
    public CommonResult<Long> createContractByQuotation(@RequestParam("id") Long id) {
        return success(quotationService.createContractByQuotation(id, getLoginUserId()));
    }

    @PostMapping("/parse-text")
    @Operation(summary = "智能解析报价单文本")
    @PreAuthorize("@ss.hasPermission('crm:quotation:create')")
    public CommonResult<CrmQuotationParseRespVO> parseQuotationText(@Valid @RequestBody CrmQuotationParseReqVO reqVO) {
        return success(doParseQuotationText(reqVO.getContent()));
    }

    private CrmQuotationParseRespVO doParseQuotationText(String content) {
        if (content == null) {
            return new CrmQuotationParseRespVO();
        }
        String text = content.replaceAll("[\\s　]+", " ").trim();
        CrmQuotationParseRespVO vo = new CrmQuotationParseRespVO();

        // 1. 电话
        Matcher phoneMatcher = Pattern.compile("(?<!\\d)(1[3-9]\\d{9}|0\\d{2,3}-?\\d{7,8})(?!\\d)").matcher(text);
        if (phoneMatcher.find()) {
            vo.setPhone(phoneMatcher.group());
        }

        // 2. 邮箱
        Matcher emailMatcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text);
        if (emailMatcher.find()) {
            vo.setEmail(emailMatcher.group());
        }

        // 3. QQ
        Matcher qqMatcher = Pattern.compile("(?:QQ|qq|企鹅)[\\s:：]*([1-9]\\d{4,11})").matcher(text);
        if (qqMatcher.find()) {
            vo.setQq(qqMatcher.group(1));
        }

        // 4. 地址
        Matcher addressPrefixMatcher = Pattern.compile("(?:地址[\\s:：]*|位于[\\s:：]*|住址[\\s:：]*)([^,，。\\n]{5,80})").matcher(text);
        if (addressPrefixMatcher.find()) {
            vo.setAddress(addressPrefixMatcher.group(1).trim());
        } else {
            Matcher addressMatcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,12}(?:省|自治区|市|区|县|镇|乡|街道|路|街|号|栋|单元|室|楼|层)[\\u4e00-\\u9fa50-9\\-#A-Za-z（）()、]{2,80})").matcher(text);
            if (addressMatcher.find()) {
                vo.setAddress(addressMatcher.group(1).trim());
            }
        }

        // 5. 联系人（带职务称呼）
        Matcher titleMatcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})(经理|先生|女士|总|工|老师)").matcher(text);
        if (titleMatcher.find()) {
            vo.setContactName(titleMatcher.group(1) + titleMatcher.group(2));
        }

        // 6. 客户/公司名称
        Matcher customerMatcher = Pattern.compile("([^,，。\\s]{3,60}(?:公司|厂|集团|股份|油田|项目部|有限公司|有限责任公司))").matcher(text);
        String bestCustomer = null;
        while (customerMatcher.find()) {
            String customer = customerMatcher.group(1).trim();
            if (bestCustomer == null || customer.length() > bestCustomer.length()) {
                bestCustomer = customer;
            }
        }
        if (bestCustomer != null) {
            vo.setCustomerName(bestCustomer);
        }

        // 7. 若没识别到联系人，尝试取手机号前的中文人名
        if (vo.getContactName() == null && vo.getPhone() != null) {
            int phoneIndex = text.indexOf(vo.getPhone());
            if (phoneIndex > 0) {
                String beforePhone = text.substring(0, phoneIndex).trim();
                Matcher nameMatcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})(?:\\s+|$)").matcher(beforePhone);
                String lastName = null;
                while (nameMatcher.find()) {
                    lastName = nameMatcher.group(1);
                }
                if (lastName != null && !lastName.equals(vo.getCustomerName())) {
                    vo.setContactName(lastName);
                }
            }
        }

        // 8. 产品项：数量+单位+（可选）单价
        List<CrmQuotationParseRespVO.Product> products = new ArrayList<>();
        Pattern unitPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(米|吨|支|套|件|个|卷|桶|kg|m|台|项|㎡|平方米|根|块|片|组)");
        Pattern pricePattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*元(?:/[^,，。\\s]{1,5})?");
        String[] segments = text.split("[,，。\\n]");
        for (String segment : segments) {
            Matcher unitMatcher = unitPattern.matcher(segment);
            while (unitMatcher.find()) {
                BigDecimal count = new BigDecimal(unitMatcher.group(1));
                String unit = unitMatcher.group(2);

                // 同一句话里找单价（优先单位后面的价格）
                String afterUnit = segment.substring(unitMatcher.end());
                BigDecimal price = null;
                Matcher priceAfterMatcher = pricePattern.matcher(afterUnit);
                if (priceAfterMatcher.find()) {
                    price = new BigDecimal(priceAfterMatcher.group(1));
                }
                if (price == null) {
                    Matcher priceBeforeMatcher = pricePattern.matcher(segment.substring(0, unitMatcher.start()));
                    if (priceBeforeMatcher.find()) {
                        price = new BigDecimal(priceBeforeMatcher.group(1));
                    }
                }

                // 产品名称取数量前面的文本，并去除价格噪音
                String productName = segment.substring(0, unitMatcher.start()).trim()
                        .replaceAll("单价|价格|报价|总价|合计|金额", "")
                        .replaceAll("(\\d+(?:\\.\\d+)?)\\s*元(?:/[^,，。\\s]{1,5})?", "")
                        .replaceAll("^[：:，,、\\s]+", "")
                        .replaceAll("[：:，,、\\s]+$", "");
                if (productName.isEmpty()) {
                    productName = "未识别产品";
                }

                CrmQuotationParseRespVO.Product product = new CrmQuotationParseRespVO.Product();
                product.setProductName(productName);
                product.setCount(count);
                product.setUnit(unit);
                product.setPrice(price);
                products.add(product);
            }
        }
        vo.setProducts(products);

        // 9. 备注：去掉已提取的关键信息后剩下的内容
        String remark = text;
        if (vo.getPhone() != null) {
            remark = remark.replace(vo.getPhone(), "");
        }
        if (vo.getEmail() != null) {
            remark = remark.replace(vo.getEmail(), "");
        }
        if (vo.getQq() != null) {
            remark = remark.replaceAll("(?:QQ|qq|企鹅)[\\s:：]*" + vo.getQq(), "");
        }
        if (vo.getAddress() != null) {
            remark = remark.replace(vo.getAddress(), "");
        }
        if (vo.getCustomerName() != null) {
            remark = remark.replace(vo.getCustomerName(), "");
        }
        if (vo.getContactName() != null) {
            remark = remark.replace(vo.getContactName(), "");
        }
        remark = remark.replaceAll("[,，。；;、]+", " ").replaceAll("\\s+", " ").trim();
        if (!remark.isEmpty()) {
            vo.setRemark(remark);
        }
        return vo;
    }

    private List<CrmQuotationRespVO> buildQuotationDetailList(List<CrmQuotationDO> quotationList) {
        if (CollUtil.isEmpty(quotationList)) {
            return Collections.emptyList();
        }
        // 1.1 获取客户列表
        Map<Long, CrmCustomerDO> customerMap = customerService.getCustomerMap(
                convertSet(quotationList, CrmQuotationDO::getCustomerId));
        // 1.2 获取创建人、负责人列表
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(convertListByFlatMap(quotationList,
                quotation -> Stream.of(NumberUtils.parseLong(quotation.getCreator()), quotation.getOwnerUserId())));
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(convertSet(userMap.values(), AdminUserRespDTO::getDeptId));
        // 1.3 获取商机
        Map<Long, CrmBusinessDO> businessMap = businessService.getBusinessMap(
                convertSet(quotationList, CrmQuotationDO::getBusinessId));
        // 2. 拼接数据
        return BeanUtils.toBean(quotationList, CrmQuotationRespVO.class, quotationVO -> {
            // 2.1 设置客户信息
            findAndThen(customerMap, quotationVO.getCustomerId(), customer -> quotationVO.setCustomerName(customer.getName()));
            // 2.2 设置用户信息
            findAndThen(userMap, Long.parseLong(quotationVO.getCreator()), user -> quotationVO.setCreatorName(user.getNickname()));
            MapUtils.findAndThen(userMap, quotationVO.getOwnerUserId(), user -> {
                quotationVO.setOwnerUserName(user.getNickname());
                MapUtils.findAndThen(deptMap, user.getDeptId(), dept -> quotationVO.setOwnerUserDeptName(dept.getName()));
            });
            // 2.3 设置商机信息
            findAndThen(businessMap, quotationVO.getBusinessId(), business -> quotationVO.setBusinessName(business.getName()));
        });
    }

}
