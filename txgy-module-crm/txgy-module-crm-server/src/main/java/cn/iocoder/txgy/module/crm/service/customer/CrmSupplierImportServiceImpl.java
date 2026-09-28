package cn.iocoder.txgy.module.crm.service.customer;

import cn.hutool.core.util.PhoneUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.module.crm.controller.admin.customer.vo.customer.CrmSupplierImportRespVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.txgy.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.txgy.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.txgy.module.crm.enums.customer.CrmCustomerTypeEnum;
import cn.iocoder.txgy.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.txgy.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.txgy.module.crm.service.permission.bo.CrmPermissionCreateReqBO;
import cn.iocoder.txgy.module.crm.util.DocxTableUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.SUPPLIER_IMPORT_NO_DATA;
import static cn.iocoder.txgy.module.crm.enums.ErrorCodeConstants.SUPPLIER_IMPORT_PARSE_FAIL;

/**
 * CRM 供应商导入 Service 实现类
 *
 * @author 防腐保温项目组
 */
@Service
@Validated
@Slf4j
public class CrmSupplierImportServiceImpl implements CrmSupplierImportService {

    /**
     * 备注最大长度（crm_customer.remark 为 varchar(500)）
     */
    private static final int REMARK_MAX_LENGTH = 500;
    /**
     * 失败明细里原始行内容的最大长度
     */
    private static final int RAW_CONTENT_MAX_LENGTH = 200;

    /**
     * 文档表格中可识别的列
     */
    private enum Field {
        NAME, CONTACT, PHONE, ADDRESS, CODE, MAIN_PRODUCTS, LEVEL, PAYMENT_TERM, DELIVERY_CYCLE, STATUS, UNKNOWN
    }

    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private CrmPermissionService permissionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrmSupplierImportRespVO importSupplierWord(byte[] content, boolean updateExisting, Long ownerUserId) {
        // 1. 解析文档表格
        List<List<List<String>>> tables;
        try {
            tables = DocxTableUtils.readTables(content);
        } catch (Exception e) {
            log.warn("[importSupplierWord][文档解析失败]", e);
            throw exception(SUPPLIER_IMPORT_PARSE_FAIL, StrUtil.blankToDefault(e.getMessage(), "未知错误"));
        }

        // 2. 逐表格、逐行解析
        CrmSupplierImportRespVO resp = new CrmSupplierImportRespVO();
        List<String> successNames = new ArrayList<>();
        List<String> updateNames = new ArrayList<>();
        List<CrmSupplierImportRespVO.Item> duplicates = new ArrayList<>();
        List<CrmSupplierImportRespVO.Item> failures = new ArrayList<>();
        int total = 0;
        int rowNo = 0;
        // 文档内按「名称」去重（同一份文档里重复出现的供应商只处理一次）
        Map<String, Integer> seenNames = new LinkedHashMap<>();

        for (List<List<String>> rows : tables) {
            if (rows.size() < 2) { // 至少要有表头 + 一行数据
                continue;
            }
            Map<Field, Integer> header = parseHeader(rows.get(0));
            if (!header.containsKey(Field.NAME)) { // 不是供应商表，跳过（只处理供应商相关数据）
                continue;
            }
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                rowNo++;
                // 2.1 空行过滤
                if (isBlankRow(row)) {
                    continue;
                }
                total++;
                String rawContent = buildRawContent(row);
                String name = clean(cell(row, header.get(Field.NAME)));
                // 2.2 名称缺失 → 解析失败，不中断整体导入
                if (StrUtil.isBlank(name)) {
                    failures.add(new CrmSupplierImportRespVO.Item(rowNo, null, rawContent, "未识别到供应商名称"));
                    continue;
                }
                // 2.3 文档内重复 → 计入重复
                String nameKey = name.replaceAll("\\s", "").toLowerCase();
                Integer firstRow = seenNames.get(nameKey);
                if (firstRow != null) {
                    duplicates.add(new CrmSupplierImportRespVO.Item(rowNo, name, rawContent,
                            StrUtil.format("文档内重复（第 {} 行已出现同名供应商）", firstRow)));
                    continue;
                }
                seenNames.put(nameKey, rowNo);

                String contact = clean(cell(row, header.get(Field.CONTACT)));
                String phone = clean(cell(row, header.get(Field.PHONE)));
                String address = clean(cell(row, header.get(Field.ADDRESS)));
                String remark = buildRemark(clean(cell(row, header.get(Field.CODE))),
                        clean(cell(row, header.get(Field.MAIN_PRODUCTS))),
                        clean(cell(row, header.get(Field.LEVEL))),
                        clean(cell(row, header.get(Field.PAYMENT_TERM))),
                        clean(cell(row, header.get(Field.DELIVERY_CYCLE))),
                        clean(cell(row, header.get(Field.STATUS))));
                // 电话若不是合法手机/座机格式，不写入电话字段（否则该客户后续无法在表单中保存），改记入备注
                boolean phoneValid = StrUtil.isNotBlank(phone) && (PhoneUtil.isTel(phone) || PhoneUtil.isPhone(phone));
                if (StrUtil.isNotBlank(phone) && !phoneValid) {
                    remark = StrUtil.maxLength(StrUtil.format("{}；联系电话：{}", remark, phone), REMARK_MAX_LENGTH);
                }

                // 2.4 已存在 → 按参数决定「跳过」还是「更新」
                CrmCustomerDO exist = customerMapper.selectByCustomerName(name);
                if (exist != null) {
                    if (!updateExisting) {
                        duplicates.add(new CrmSupplierImportRespVO.Item(rowNo, name, rawContent,
                                "系统中已存在同名" + customerTypeLabel(exist.getCustomerType())));
                        continue;
                    }
                    updateExistSupplier(exist, contact, phoneValid ? phone : null, address, remark,
                            ownerUserId);
                    updateNames.add(name);
                    continue;
                }

                // 2.5 新增供应商
                CrmCustomerDO customer = buildCustomer(name, contact, phoneValid ? phone : null, address,
                        remark, ownerUserId);
                customerMapper.insert(customer);
                // 数据权限：负责人 OWNER
                permissionService.createPermission(new CrmPermissionCreateReqBO()
                        .setBizType(CrmBizTypeEnum.CRM_CUSTOMER.getType()).setBizId(customer.getId())
                        .setUserId(ownerUserId).setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));
                successNames.add(name);
            }
        }

        // 3. 文档里没有可识别的供应商数据 → 明确报错，避免用户误以为导入成功
        if (total == 0) {
            throw exception(SUPPLIER_IMPORT_NO_DATA);
        }

        // 4. 汇总
        resp.setTotalCount(total);
        resp.setSuccessCount(successNames.size());
        resp.setUpdateCount(updateNames.size());
        resp.setDuplicateCount(duplicates.size());
        resp.setFailCount(failures.size());
        resp.setSuccessNames(successNames);
        resp.setUpdateNames(updateNames);
        resp.setDuplicates(duplicates);
        resp.setFailures(failures);
        return resp;
    }

    /**
     * 解析表头，得到「列类型 -> 列下标」
     */
    private static Map<Field, Integer> parseHeader(List<String> headerRow) {
        Map<Field, Integer> header = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            Field field = matchHeader(clean(headerRow.get(i)));
            if (field != Field.UNKNOWN && !header.containsKey(field)) {
                header.put(field, i);
            }
        }
        return header;
    }

    /**
     * 表头名称 -> 列类型（表头驱动，列顺序变化、缺少列都不影响）
     */
    private static Field matchHeader(String raw) {
        if (StrUtil.isBlank(raw)) {
            return Field.UNKNOWN;
        }
        // 去掉空白与常见分隔符后再匹配
        String h = raw.replaceAll("[\\s\\u00A0\\u3000:：()（）/\\\\\\-_]", "");
        if (h.contains("联系人")) {
            return Field.CONTACT;
        }
        if (h.contains("名称")) { // 供应商名称 / 客户名称 / 公司名称 / 名称
            return Field.NAME;
        }
        if (h.contains("联系电话") || h.contains("联系方式") || h.contains("电话") || h.contains("手机")) {
            return Field.PHONE;
        }
        if (h.contains("地址")) {
            return Field.ADDRESS;
        }
        if (h.contains("编号") || h.contains("编码")) {
            return Field.CODE;
        }
        if (h.contains("主营") || h.contains("产品")) {
            return Field.MAIN_PRODUCTS;
        }
        if (h.contains("等级") || h.contains("级别")) {
            return Field.LEVEL;
        }
        if (h.contains("账期") || h.contains("付款")) {
            return Field.PAYMENT_TERM;
        }
        if (h.contains("交货")) {
            return Field.DELIVERY_CYCLE;
        }
        if (h.contains("状态")) {
            return Field.STATUS;
        }
        return Field.UNKNOWN;
    }

    private static CrmCustomerDO buildCustomer(String name, String contact, String phone, String address,
                                               String remark, Long ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        CrmCustomerDO customer = new CrmCustomerDO()
                .setName(name)
                .setContactName(contact)
                .setDetailAddress(address)
                .setRemark(StrUtil.emptyToNull(remark))
                .setCustomerType(CrmCustomerTypeEnum.UPSTREAM.getType())
                .setOwnerUserId(ownerUserId).setOwnerTime(now)
                .setFollowUpStatus(false).setLockStatus(false).setDealStatus(false);
        applyPhone(customer, phone);
        return customer;
    }

    /**
     * 更新已存在的供应商：只更新文档中有值的字段，不覆盖其它信息，也不改变已有的客户类别
     */
    private static void updateExistSupplier(CrmCustomerDO exist, String contact, String phone, String address,
                                            String remark, Long ownerUserId) {
        CrmCustomerDO update = new CrmCustomerDO().setId(exist.getId());
        if (StrUtil.isNotBlank(contact)) {
            update.setContactName(contact);
        }
        if (StrUtil.isNotBlank(phone)) {
            applyPhone(update, phone);
        }
        if (StrUtil.isNotBlank(address)) {
            update.setDetailAddress(address);
        }
        // 备注仅在原备注为空时写入，避免覆盖用户手工维护的内容
        if (StrUtil.isBlank(exist.getRemark()) && StrUtil.isNotBlank(remark)) {
            update.setRemark(StrUtil.maxLength(remark, REMARK_MAX_LENGTH));
        }
        // 原客户类别为空时补齐为「上游供应商」；已是下游客户则保持不变
        if (exist.getCustomerType() == null) {
            update.setCustomerType(CrmCustomerTypeEnum.UPSTREAM.getType());
        }
        // 无负责人时补上负责人，保证新导入的供应商能被下拉读到
        if (exist.getOwnerUserId() == null && ownerUserId != null) {
            update.setOwnerUserId(ownerUserId).setOwnerTime(LocalDateTime.now());
        }
    }

    private static void applyPhone(CrmCustomerDO customer, String phone) {
        if (StrUtil.isBlank(phone)) {
            return;
        }
        if (PhoneUtil.isMobile(phone)) {
            customer.setMobile(phone);
        } else {
            customer.setTelephone(phone);
        }
    }

    /**
     * 把文档中的扩展列（编号/主营产品/等级/账期/交货周期/状态）拼成备注，保证信息不丢
     */
    private static String buildRemark(String code, String mainProducts, String level, String paymentTerm,
                                      String deliveryCycle, String status) {
        List<String> parts = new ArrayList<>();
        if (StrUtil.isNotBlank(code)) {
            parts.add("供应商编号：" + code);
        }
        if (StrUtil.isNotBlank(mainProducts)) {
            parts.add("主营产品：" + mainProducts);
        }
        if (StrUtil.isNotBlank(level)) {
            parts.add("等级：" + level);
        }
        if (StrUtil.isNotBlank(paymentTerm)) {
            parts.add("账期：" + paymentTerm);
        }
        if (StrUtil.isNotBlank(deliveryCycle)) {
            parts.add("交货周期：" + deliveryCycle);
        }
        if (StrUtil.isNotBlank(status)) {
            parts.add("状态：" + status);
        }
        return StrUtil.maxLength(String.join("；", parts), REMARK_MAX_LENGTH);
    }

    private static String cell(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return null;
        }
        return row.get(index);
    }

    /**
     * 清洗单元格：全角空格转半角、合并多余空白与无效换行
     */
    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace('\u00A0', ' ').replace('\u3000', ' ')
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean isBlankRow(List<String> row) {
        for (String cell : row) {
            if (StrUtil.isNotBlank(cell) && StrUtil.isNotBlank(cell.replaceAll("\\s+", ""))) {
                return false;
            }
        }
        return true;
    }

    private static String buildRawContent(List<String> row) {
        return StrUtil.maxLength(String.join(" | ", row), RAW_CONTENT_MAX_LENGTH);
    }

    private static String customerTypeLabel(Integer customerType) {
        String name = CrmCustomerTypeEnum.getNameByType(customerType);
        return name == null ? "客户" : name;
    }

}
