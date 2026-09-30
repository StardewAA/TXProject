package cn.iocoder.txgy.module.wms.service.ai.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.brand.WmsItemBrandSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.category.WmsItemCategorySaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.item.WmsItemSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.item.vo.sku.WmsItemSkuSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.merchant.vo.WmsMerchantSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.md.warehouse.vo.WmsWarehouseSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemBrandDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemCategoryDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemBrandMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemCategoryMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.merchant.WmsMerchantMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.enums.md.WmsMerchantTypeEnum;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataCreateService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemBrandService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemCategoryService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemService;
import cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * WMS AI 主数据建档 Service 实现
 *
 * <p>建档前一律先做名称查重，同名已存在则提示或直接复用；编码由系统按规则自动生成，
 * 不采用大模型输出的编码。各 createXxx 均由本实现调用既有模块 Service，权限/校验不绕过。
 *
 * <p>fields 字段约定（key -> 语义，缺失即空）：
 * <ul>
 *   <li>category：name 必填；parentName 可选（当前仅支持根分类自动创建）</li>
 *   <li>brand：name 必填</li>
 *   <li>merchant：name、kind 必填（supplier=供应商/customer=客户/both=客户兼供应商）；
 *       contact/mobile/telephone/address/email/remark 可选</li>
 *   <li>warehouse：name 必填；remark 可选</li>
 *   <li>item：name 必填；categoryName（空则询问并给出候选分类）；unit/brandName/specs/barCode/
 *       costPrice/sellingPrice/weight/remark 可选；specs 为空时按"默认规格"创建首个 SKU；weight 支持 "10kg"/"500克"，统一换算 kg 落毛重/净重</li>
 * </ul>
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class WmsAiMasterDataCreateServiceImpl implements WmsAiMasterDataCreateService {

    private static final int CATEGORY_CANDIDATE_LIMIT = 8;

    @Resource
    private WmsItemService itemService;
    @Resource
    private WmsItemCategoryService itemCategoryService;
    @Resource
    private WmsItemBrandService itemBrandService;
    @Resource
    private WmsMerchantService merchantService;
    @Resource
    private WmsWarehouseService warehouseService;

    @Resource
    private WmsItemMapper itemMapper;
    @Resource
    private WmsItemCategoryMapper itemCategoryMapper;
    @Resource
    private WmsItemBrandMapper itemBrandMapper;
    @Resource
    private WmsMerchantMapper merchantMapper;
    @Resource
    private WmsWarehouseMapper warehouseMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(String domain, JSONObject fields) {
        if (fields == null) {
            fields = new JSONObject();
        }
        switch (domain == null ? "" : domain) {
            case "category":
                return createCategory(fields);
            case "brand":
                return createBrand(fields);
            case "merchant":
                return createMerchant(fields);
            case "warehouse":
                return createWarehouse(fields);
            case "item":
                return createItem(fields);
            default:
                return "暂不支持该类型的建档，可试试：新建供应商/客户、新增仓库、新增商品分类/品牌、新建商品档案。";
        }
    }

    // ==================== 分类 ====================

    private String createCategory(JSONObject fields) {
        String name = trim(fields.getStr("name"));
        if (StrUtil.isBlank(name)) {
            return "新建商品分类需要名称，请告诉我分类叫什么，例如「新建分类 保温材料」。";
        }
        // 支持子分类：有 parentName 时挂到父分类下，并做同级查重
        String parentName = trim(fields.getStr("parentName"));
        Long parentId = WmsItemCategoryDO.PARENT_ID_ROOT;
        String parentDesc = "";
        if (StrUtil.isNotBlank(parentName)) {
            WmsItemCategoryDO parent = findCategoryByName(parentName);
            if (parent == null) {
                List<String> roots = listRootCategoryNames();
                return "没有找到父分类「" + parentName + "」，无法把「" + name + "」挂到它下面。"
                        + (roots.isEmpty()
                        ? "系统当前还没有任何分类，请先建父分类（例如：新建分类 " + parentName + "）。"
                        : "现有分类有：" + String.join("、", roots) + "。请确认父分类名，"
                        + "或直接回复「新建分类 " + name + "」建为根分类。");
            }
            parentId = parent.getId();
            parentDesc = "，父分类「" + parent.getName() + "」";
        }
        // 同级查重（根分类之间、或同一父分类下）
        WmsItemCategoryDO exist = itemCategoryMapper.selectByParentIdAndName(parentId, name);
        if (exist != null) {
            return "商品分类「" + name + "」" + (StrUtil.isBlank(parentName) ? "" : "（" + parentName + " 下）")
                    + "已存在（编码 " + exist.getCode() + "），无需重复新建。";
        }
        WmsItemCategorySaveReqVO req = new WmsItemCategorySaveReqVO();
        req.setParentId(parentId);
        req.setCode(genCode("CAT"));
        req.setName(name);
        req.setSort(nextCategorySort());
        req.setStatus(CommonStatusEnum.ENABLE.getStatus());
        Long id = itemCategoryService.createItemCategory(req);
        return "已新建商品分类「" + name + "」（编码 " + req.getCode() + parentDesc + "）。";
    }

    // ==================== 品牌 ====================

    private String createBrand(JSONObject fields) {
        String name = trim(fields.getStr("name"));
        if (StrUtil.isBlank(name)) {
            return "新建品牌需要名称，请告诉我品牌叫什么，例如「新增品牌 华美」。";
        }
        WmsItemBrandDO exist = itemBrandMapper.selectByName(name);
        if (exist != null) {
            return "品牌「" + name + "」已存在，无需重复新建。";
        }
        WmsItemBrandSaveReqVO req = new WmsItemBrandSaveReqVO();
        req.setCode(genCode("BRD"));
        req.setName(name);
        Long id = itemBrandService.createItemBrand(req);
        return "已新建品牌「" + name + "」。";
    }

    // ==================== 往来企业 ====================

    private String createMerchant(JSONObject fields) {
        String name = trim(fields.getStr("name"));
        if (StrUtil.isBlank(name)) {
            return "新建往来企业需要名称，请告诉我企业叫什么，例如「新增供应商 河北华美保温材料有限公司」。";
        }
        String kind = trim(fields.getStr("kind"));
        Integer type;
        String typeName;
        if ("supplier".equalsIgnoreCase(kind)) {
            type = WmsMerchantTypeEnum.SUPPLIER.getType();
            typeName = "供应商";
        } else if ("customer".equalsIgnoreCase(kind)) {
            type = WmsMerchantTypeEnum.CUSTOMER.getType();
            typeName = "客户";
        } else if ("both".equalsIgnoreCase(kind)) {
            type = WmsMerchantTypeEnum.CUSTOMER_SUPPLIER.getType();
            typeName = "客户/供应商";
        } else {
            return "新建往来企业「" + name + "」需要明确它是「供应商」还是「客户」，例如回复：它是供应商。";
        }
        WmsMerchantDO exist = merchantMapper.selectByName(name);
        if (exist != null) {
            return "往来企业「" + name + "」已存在（编码 " + exist.getCode() + "），无需重复新建。";
        }
        WmsMerchantSaveReqVO req = new WmsMerchantSaveReqVO();
        req.setCode(genCode(type == WmsMerchantTypeEnum.CUSTOMER.getType() ? "C" : "S"));
        req.setName(name);
        req.setType(type);
        req.setContact(trim(fields.getStr("contact")));
        req.setMobile(trim(fields.getStr("mobile")));
        req.setTelephone(trim(fields.getStr("telephone")));
        req.setAddress(trim(fields.getStr("address")));
        req.setEmail(trim(fields.getStr("email")));
        req.setRemark(trim(fields.getStr("remark")));
        merchantService.createMerchant(req);
        return "已新建" + typeName + "「" + name + "」（编码 " + req.getCode() + "）。";
    }

    // ==================== 仓库 ====================

    private String createWarehouse(JSONObject fields) {
        String name = trim(fields.getStr("name"));
        if (StrUtil.isBlank(name)) {
            return "新建仓库需要名称，请告诉我仓库叫什么，例如「新增仓库 成品三库」。";
        }
        WmsWarehouseDO exist = warehouseMapper.selectByName(name);
        if (exist != null) {
            return "仓库「" + name + "」已存在（编码 " + exist.getCode() + "），无需重复新建。";
        }
        WmsWarehouseSaveReqVO req = new WmsWarehouseSaveReqVO();
        req.setCode(genCode("WH"));
        req.setName(name);
        req.setSort((int) (System.currentTimeMillis() % 100000));
        req.setRemark(trim(fields.getStr("remark")));
        warehouseService.createWarehouse(req);
        return "已新建仓库「" + name + "」（编码 " + req.getCode() + "）。";
    }

    // ==================== 商品（含自动补分类/品牌） ====================

    private String createItem(JSONObject fields) {
        String name = trim(fields.getStr("name"));
        // 兼容大模型把商品名填到 names 数组的情况
        if (StrUtil.isBlank(name)) {
            JSONArray arr = fields.getJSONArray("names");
            if (arr != null) {
                for (Object o : arr) {
                    String s = trim(String.valueOf(o));
                    if (StrUtil.isNotBlank(s)) {
                        name = s;
                        break;
                    }
                }
            }
        }
        if (StrUtil.isBlank(name)) {
            return "新建商品需要名称，请告诉我商品叫什么。";
        }
        // 最终防线：拒绝把「说明性文字」落成商品档案。
        // 触发场景：缺品建档反问后用户回复「分类=保温材料」，系统把该回复拼回原文重新识别，
        // 大模型会把「（用户补充：补充建档信息：分类=保温材料）」整串当成商品名再建一次，
        // 从而生成「补充建档信息：分类=保温材料」这种垃圾档案。
        if (looksLikeInstructionText(name)) {
            log.warn("[ai-create] 拒绝创建疑似说明性文字的商品：{}", name);
            return "没识别到有效的商品名称（「" + name + "」看起来是补充说明而不是商品名）。"
                    + "请直接告诉我商品名，例如「新建商品 岩棉板」；若要同时指定分类/单位，"
                    + "请写成「新建商品 岩棉板 分类=保温材料 单位=立方米」。";
        }
        WmsItemDO exist = itemMapper.selectByName(name);
        if (exist != null) {
            return "商品「" + name + "」在【商品管理】中已存在，无需重复新建。若需要我可以帮你查它的库存或直接开入库单。";
        }
        // 1. 分类：优先匹配现有分类；没有分类词时给出候选让用户选（不强挂）
        String categoryName = trim(fields.getStr("categoryName"));
        Long categoryId = null;
        if (StrUtil.isBlank(categoryName)) {
            List<String> roots = listRootCategoryNames();
            if (roots.isEmpty()) {
                return "新建商品「" + name + "」还需要指定「商品分类」——系统还没有任何分类，请先回复分类名"
                        + "（例如：分类=保温材料），我会一并创建分类和商品。";
            }
            return "新建商品「" + name + "」还需要 2 项信息：\n"
                    + "1) 商品分类（现有分类：" + String.join("、", roots) + "，或直接给我一个新分类名，我会自动创建）\n"
                    + "2) 单位（如 箱 / 袋 / kg / 件）\n"
                    + "请补充，例如：分类=保温材料，单位=箱。";
        } else {
            WmsItemCategoryDO cat = findCategoryByName(categoryName);
            if (cat == null) {
                WmsItemCategoryDO category = new WmsItemCategoryDO();
                category.setParentId(WmsItemCategoryDO.PARENT_ID_ROOT);
                category.setCode(genCode("CAT"));
                category.setName(categoryName);
                category.setSort(nextCategorySort());
                category.setStatus(CommonStatusEnum.ENABLE.getStatus());
                itemCategoryMapper.insert(category);
                categoryId = category.getId();
                log.info("[createItem] 自动补建分类 {} id={}", categoryName, categoryId);
            } else {
                categoryId = cat.getId();
            }
        }
        // 2. 单位：用户没说时不强问（可空），提示默认
        String unit = trim(fields.getStr("unit"));
        // 3. 品牌：匹配/复用/新建
        Long brandId = null;
        String brandName = trim(fields.getStr("brandName"));
        if (StrUtil.isNotBlank(brandName)) {
            WmsItemBrandDO brand = itemBrandMapper.selectByName(brandName);
            if (brand != null) {
                brandId = brand.getId();
            } else {
                WmsItemBrandDO brandDo = new WmsItemBrandDO();
                brandDo.setCode(genCode("BRD"));
                brandDo.setName(brandName);
                itemBrandMapper.insert(brandDo);
                brandId = brandDo.getId();
                log.info("[createItem] 自动补建品牌 {} id={}", brandName, brandId);
            }
        }
        // 4. SKU：用户给了规格就按规格建，否则默认规格
        List<WmsItemSkuSaveReqVO> skuReqs = new ArrayList<>();
        List<String> specs = extractSpecs(fields);
        if (CollUtil.isEmpty(specs)) {
            specs = new ArrayList<>();
            specs.add("默认规格");
        }
        for (String spec : specs) {
            WmsItemSkuSaveReqVO skuReq = new WmsItemSkuSaveReqVO();
            skuReq.setName(spec);
            skuReq.setCode(genCode("SKU"));
            skuReq.setBarCode(trim(fields.getStr("barCode")));
            skuReq.setCostPrice(parseBigDecimal(fields.get("costPrice")));
            skuReq.setSellingPrice(parseBigDecimal(fields.get("sellingPrice")));
            BigDecimal weightKg = parseWeightKg(fields.get("weight"));
            if (weightKg != null) {
                skuReq.setGrossWeight(weightKg);
                skuReq.setNetWeight(weightKg);
            }
            skuReqs.add(skuReq);
        }
        // 5. 建档
        WmsItemSaveReqVO req = new WmsItemSaveReqVO();
        req.setCode(genCode("SP"));
        req.setName(name);
        req.setCategoryId(categoryId);
        req.setUnit(unit);
        req.setBrandId(brandId);
        req.setRemark(trim(fields.getStr("remark")));
        req.setSkus(skuReqs);
        itemService.createItem(req);

        String specText = specs.size() == 1 ? specs.get(0) : specs.stream().limit(2).collect(Collectors.joining("、"))
                + (specs.size() > 2 ? " 等" + specs.size() + " 种规格" : "");
        BigDecimal costPriceVal = parseBigDecimal(fields.get("costPrice"));
        return "已新建商品「" + name + "」（编码 " + req.getCode() + "）"
                + (categoryId != null ? "，分类「" + categoryName + "」" : "")
                + (StrUtil.isNotBlank(brandName) ? "，品牌「" + brandName + "」" : "")
                + (StrUtil.isNotBlank(unit) ? "，单位「" + unit + "」" : "")
                + "，规格「" + specText + "」"
                + (costPriceVal != null ? "，成本价 " + costPriceVal.stripTrailingZeros().toPlainString() + " 元" : "")
                + (parseWeightKg(fields.get("weight")) != null ? "，重量 " + StrUtil.trim(String.valueOf(fields.get("weight"))) : "")
                + "。现在就可以继续做入库/出库/移库啦。";
    }

    // ==================== 工具 ====================

    /**
     * 判断候选名是否其实是「补充说明文字」而不是商品名。
     *
     * <p>典型脏数据：「补充建档信息：分类=保温材料」「（用户补充：…）」——
     * 商品名里不会出现等号形式的字段表达式或这些标记词。
     */
    private boolean looksLikeInstructionText(String name) {
        if (StrUtil.isBlank(name)) {
            return true;
        }
        String s = name.trim();
        // 1. 元信息标记词
        if (s.contains("用户补充") || s.contains("补充建档信息") || s.contains("补充信息")
                || s.contains("自动建档") || s.contains("清单里") || s.contains("建档前")
                || s.contains("待新建") || s.contains("未建档")) {
            return true;
        }
        // 2. 去掉「补充/说明/备注」前缀后，若以「字段=值」开头，说明整串是字段说明
        String core = s.replaceAll("^(补充|说明|备注|注明|追加)[^=：:]{0,8}[：:]?", "");
        String[] keys = {"分类", "单位", "品牌", "价格", "单价", "重量", "规格", "条码", "类目"};
        for (String k : keys) {
            if (core.startsWith(k + "=") || core.startsWith(k + "：") || core.startsWith(k + ":")) {
                return true;
            }
        }
        // 3. 命中 2 个以上「字段=值」，基本是参数说明
        int kv = 0;
        for (String k : keys) {
            if (core.contains(k + "=") || core.contains(k + "：")) {
                kv++;
            }
        }
        if (kv >= 2) {
            return true;
        }
        // 4. 长度异常 / 以句号结尾 / 含换行 —— 都是整句说明而非名称
        return s.length() > 30 || s.endsWith("。") || s.contains("\n");
    }

    private String trim(String s) {
        return StrUtil.isBlank(s) ? "" : s.trim();
    }

    private BigDecimal parseBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        String s = StrUtil.trim(v.toString());
        if (StrUtil.isBlank(s) || "null".equalsIgnoreCase(s) || "undefined".equalsIgnoreCase(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> extractSpecs(JSONObject fields) {
        List<String> specs = new ArrayList<>();
        JSONArray arr = fields.getJSONArray("specs");
        if (arr != null) {
            for (Object o : arr) {
                String s = trim(String.valueOf(o));
                if (StrUtil.isNotBlank(s)) {
                    specs.add(s);
                }
            }
        }
        if (specs.isEmpty() && StrUtil.isNotBlank(fields.getStr("spec"))) {
            specs.add(trim(fields.getStr("spec")));
        }
        return specs;
    }

    /**
     * 重量解析为 kg（BigDecimal）：支持 "10kg" / "500克" / "1.5公斤" / 纯数字（默认 kg）。
     * 解析失败返回 null，不阻塞建档。
     */
    private BigDecimal parseWeightKg(Object v) {
        if (v == null) {
            return null;
        }
        String s = StrUtil.trim(v.toString());
        if (StrUtil.isBlank(s) || "null".equalsIgnoreCase(s)) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([0-9]+(?:\\.[0-9]+)?)\\s*(千克|公斤|kg|克|g)?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(s);
        if (!m.find()) {
            return null;
        }
        try {
            BigDecimal num = new BigDecimal(m.group(1));
            String unit = m.group(2);
            if (unit != null && (unit.contains("克") || "g".equalsIgnoreCase(unit))) {
                num = num.divide(new BigDecimal("1000"), 3, java.math.RoundingMode.HALF_UP);
            }
            return num;
        } catch (Exception ex) {
            return null;
        }
    }

    private WmsItemCategoryDO findCategoryByName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        String n = name.trim();
        return itemCategoryMapper.selectOne(new LambdaQueryWrapperX<WmsItemCategoryDO>()
                .eq(WmsItemCategoryDO::getName, n).last("LIMIT 1"));
    }

    private WmsItemCategoryDO findRootCategoryByName(String name) {
        return itemCategoryMapper.selectByParentIdAndName(WmsItemCategoryDO.PARENT_ID_ROOT, name);
    }

    private List<String> listRootCategoryNames() {
        List<WmsItemCategoryDO> cats = itemCategoryMapper.selectList(new LambdaQueryWrapperX<WmsItemCategoryDO>()
                .eq(WmsItemCategoryDO::getParentId, WmsItemCategoryDO.PARENT_ID_ROOT)
                .eq(WmsItemCategoryDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .orderByAsc(WmsItemCategoryDO::getSort)
                .orderByAsc(WmsItemCategoryDO::getId));
        return cats.stream().map(WmsItemCategoryDO::getName)
                .limit(CATEGORY_CANDIDATE_LIMIT).collect(Collectors.toList());
    }

    private int nextCategorySort() {
        return (int) (System.currentTimeMillis() % 100000);
    }

    /**
     * 自动编码：前缀 + 毫秒时间 + 随机尾号，保证短且唯一概率极高
     */
    private String genCode(String prefix) {
        String millis = String.valueOf(System.currentTimeMillis());
        String tail = millis.length() > 9 ? millis.substring(millis.length() - 9) : millis;
        return prefix + tail + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

}
