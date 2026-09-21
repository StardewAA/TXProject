package cn.iocoder.txgy.module.wms.service.ai.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
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
import cn.iocoder.txgy.module.wms.service.ai.WmsAiDeleteResult;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataDeleteService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemBrandService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemCategoryService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemService;
import cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 删除主数据服务实现。
 *
 * <p>按名称精确查找后删除；找不到给出候选引导；
 * 底层业务异常（分类下有子分类/商品等）转成自然语言返回。</p>
 */
@Service
@Slf4j
public class WmsAiMasterDataDeleteServiceImpl implements WmsAiMasterDataDeleteService {

    @Resource
    private WmsItemCategoryService itemCategoryService;
    @Resource
    private WmsItemCategoryMapper itemCategoryMapper;
    @Resource
    private WmsItemBrandService itemBrandService;
    @Resource
    private WmsItemBrandMapper itemBrandMapper;
    @Resource
    private WmsItemService itemService;
    @Resource
    private WmsItemMapper itemMapper;
    @Resource
    private WmsWarehouseService warehouseService;
    @Resource
    private WmsWarehouseMapper warehouseMapper;
    @Resource
    private WmsMerchantService merchantService;
    @Resource
    private WmsMerchantMapper merchantMapper;

    @Override
    public WmsAiDeleteResult tryDelete(String domain, JSONObject intent) {
        String name = extractName(intent);
        if (StrUtil.isBlank(name)) {
            return WmsAiDeleteResult.fail("请告诉我要删除的名称，例如：删除分类 保温材料。");
        }
        switch (StrUtil.nullToEmpty(domain).trim()) {
            case "category":
                return tryDeleteCategory(name);
            case "brand":
                return WmsAiDeleteResult.success(deleteBrand(name));
            case "merchant":
                return WmsAiDeleteResult.success(deleteMerchant(name));
            case "warehouse":
                return WmsAiDeleteResult.success(deleteWarehouse(name));
            case "item":
                return WmsAiDeleteResult.success(deleteItem(name));
            default:
                return WmsAiDeleteResult.fail("暂不支持删除「" + domain + "」，目前可以删除：商品分类 / 品牌 / 往来企业 / 仓库 / 商品。");
        }
    }

    /**
     * 从意图参数中抽取名称，优先取 name，其次取 names 数组第一个。
     */
    private String extractName(JSONObject intent) {
        String name = trim(intent == null ? null : intent.getStr("name"));
        if (StrUtil.isBlank(name) && intent != null) {
            JSONArray names = intent.getJSONArray("names");
            if (names != null) {
                for (Object o : names) {
                    String s = trim(String.valueOf(o));
                    if (StrUtil.isNotBlank(s)) {
                        name = s;
                        break;
                    }
                }
            }
        }
        return name;
    }

    private WmsAiDeleteResult tryDeleteCategory(String name) {
        WmsItemCategoryDO category = findCategoryByName(name);
        if (category == null) {
            return WmsAiDeleteResult.fail("没有找到商品分类「" + name + "」，请确认名称是否正确。你也可以问「商品分类有哪些」查看现有分类。");
        }
        if (itemService.getItemCountByCategoryId(category.getId()) > 0L) {
            return WmsAiDeleteResult.fail("分类「" + category.getName() + "」下还有商品，请先把这些商品移走或删除后再操作。");
        }
        List<WmsItemCategoryDO> children = findAllChildren(category.getId());
        if (CollUtil.isEmpty(children)) {
            try {
                itemCategoryService.deleteItemCategory(category.getId());
            } catch (Exception ex) {
                return WmsAiDeleteResult.fail(translateDeleteError(ex, "分类", name));
            }
            return WmsAiDeleteResult.success("已删除商品分类「" + category.getName() + "」（编码 " + category.getCode() + "）。");
        }
        List<String> childNames = children.stream().map(WmsItemCategoryDO::getName).distinct().collect(Collectors.toList());
        StringBuilder sb = new StringBuilder();
        sb.append("分类「").append(category.getName()).append("」下还有 ").append(childNames.size()).append(" 个子类：\n");
        for (String cn : childNames) {
            sb.append("• ").append(cn).append("\n");
        }
        sb.append("\n确认要一起删除吗？回复「确认删除」即可删除该分类及其所有子类。");
        return WmsAiDeleteResult.confirm(sb.toString(), category.getId(), category.getName(), childNames);
    }

    private String deleteCategoryCascade(String name) {
        WmsItemCategoryDO category = findCategoryByName(name);
        if (category == null) {
            return "没有找到商品分类「" + name + "」，请确认名称是否正确。";
        }
        if (itemService.getItemCountByCategoryId(category.getId()) > 0L) {
            return "分类「" + category.getName() + "」下还有商品，请先把这些商品移走或删除后再操作。";
        }
        List<WmsItemCategoryDO> children = findAllChildren(category.getId());
        for (WmsItemCategoryDO child : children) {
            if (itemService.getItemCountByCategoryId(child.getId()) > 0L) {
                return "子分类「" + child.getName() + "」下还有商品，请先把这些商品移走或删除后再操作。";
            }
        }
        children.sort(Comparator.comparingInt(this::getDepth).reversed());
        try {
            for (WmsItemCategoryDO child : children) {
                itemCategoryMapper.deleteById(child.getId());
            }
            itemCategoryMapper.deleteById(category.getId());
        } catch (Exception ex) {
            return translateDeleteError(ex, "分类", name);
        }
        return "已删除商品分类「" + category.getName() + "」及其 " + children.size() + " 个子类。";
    }

    private List<WmsItemCategoryDO> findAllChildren(Long parentId) {
        List<WmsItemCategoryDO> result = new ArrayList<>();
        Set<Long> frontier = new HashSet<>();
        frontier.add(parentId);
        while (!frontier.isEmpty()) {
            List<WmsItemCategoryDO> batch = itemCategoryMapper.selectListByParentIds(frontier);
            if (CollUtil.isEmpty(batch)) {
                break;
            }
            result.addAll(batch);
            frontier = batch.stream().map(WmsItemCategoryDO::getId).collect(Collectors.toSet());
        }
        return result;
    }

    private int getDepth(WmsItemCategoryDO category) {
        int depth = 0;
        Long parentId = category.getParentId();
        while (parentId != null && !WmsItemCategoryDO.PARENT_ID_ROOT.equals(parentId)) {
            depth++;
            WmsItemCategoryDO parent = itemCategoryMapper.selectById(parentId);
            if (parent == null) {
                break;
            }
            parentId = parent.getParentId();
        }
        return depth;
    }

    private String deleteBrand(String name) {
        WmsItemBrandDO brand = itemBrandMapper.selectByName(name);
        if (brand == null) {
            return "没有找到品牌「" + name + "」，请确认名称是否正确。你也可以问「品牌有哪些」查看现有品牌。";
        }
        try {
            itemBrandService.deleteItemBrand(brand.getId());
        } catch (Exception ex) {
            return translateDeleteError(ex, "品牌", name);
        }
        return "已删除品牌「" + brand.getName() + "」。";
    }

    private String deleteMerchant(String name) {
        WmsMerchantDO merchant = merchantMapper.selectByName(name);
        if (merchant == null) {
            return "没有找到往来企业「" + name + "」，请确认名称是否正确。你也可以问「供应商有哪些」查看。";
        }
        try {
            merchantService.deleteMerchant(merchant.getId());
        } catch (Exception ex) {
            return translateDeleteError(ex, "往来企业", name);
        }
        return "已删除往来企业「" + merchant.getName() + "」（编码 " + merchant.getCode() + "）。";
    }

    private String deleteWarehouse(String name) {
        WmsWarehouseDO warehouse = warehouseMapper.selectByName(name);
        if (warehouse == null) {
            return "没有找到仓库「" + name + "」，请确认名称是否正确。你也可以问「仓库有哪些」查看现有仓库。";
        }
        try {
            warehouseService.deleteWarehouse(warehouse.getId());
        } catch (Exception ex) {
            return translateDeleteError(ex, "仓库", name);
        }
        return "已删除仓库「" + warehouse.getName() + "」（编码 " + warehouse.getCode() + "）。";
    }

    private String deleteItem(String name) {
        // 先精确匹配；找不到再按关键词模糊匹配——列表里商品名往往带规格（如「岩棉板 1200×600×50」），
        // 用户通常只说「岩棉板」，若只允许精确匹配就会一直提示「没有找到」。
        WmsItemDO item = itemMapper.selectByName(name);
        if (item == null) {
            List<WmsItemDO> candidates = itemMapper.selectList(
                    new LambdaQueryWrapperX<WmsItemDO>()
                            .and(w -> w.like(WmsItemDO::getName, name).or().like(WmsItemDO::getCode, name))
                            .last("LIMIT 6"));
            if (CollUtil.isEmpty(candidates)) {
                return "没有找到商品「" + name + "」，请确认名称是否正确。你也可以问「有哪些商品」查看现有商品。";
            }
            if (candidates.size() > 1) {
                String options = candidates.stream()
                        .map(c -> "「" + c.getName() + "（编码 " + c.getCode() + "）」")
                        .collect(Collectors.joining("、"));
                return "「" + name + "」匹配到多个商品：" + options + "。请把完整名称发我，我再删除。";
            }
            item = candidates.get(0);
        }
        try {
            itemService.deleteItem(item.getId());
        } catch (Exception ex) {
            return translateDeleteError(ex, "商品", name);
        }
        return "已删除商品「" + item.getName() + "」（编码 " + item.getCode() + "）。";
    }

    private WmsItemCategoryDO findCategoryByName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        return itemCategoryMapper.selectOne(new LambdaQueryWrapperX<WmsItemCategoryDO>()
                .eq(WmsItemCategoryDO::getName, name.trim()).last("LIMIT 1"));
    }

    private String translateDeleteError(Exception ex, String domainLabel, String name) {
        String msg = StrUtil.nullToEmpty(ex.getMessage());
        log.warn("[ai-delete] 删除{}{}「{}」失败：{}", domainLabel, name, msg, ex);
        if (msg.contains("子分类") || msg.contains("children")) {
            return domainLabel + "「" + name + "」下面还有子分类，请先删除子分类后再删除它。";
        }
        if (msg.contains("商品") && (msg.contains("存在") || msg.contains("不为空") || msg.contains("under"))) {
            return domainLabel + "「" + name + "」下还有商品，请先把这些商品移走或删除后再操作。";
        }
        if (msg.contains("引用") || msg.contains("已被") || msg.contains("使用")) {
            return domainLabel + "「" + name + "」已被其他数据引用，暂时不能删除。";
        }
        return "删除" + domainLabel + "「" + name + "」失败："
                + (StrUtil.isBlank(msg) ? "系统校验未通过，请检查该数据是否被引用。" : msg);
    }

    @Override
    public String forceDelete(String domain, JSONObject intent) {
        String name = extractName(intent);
        if (StrUtil.isBlank(name)) {
            return "请告诉我要删除的名称，例如：删除分类 保温材料。";
        }
        switch (StrUtil.nullToEmpty(domain).trim()) {
            case "category":
                return deleteCategoryCascade(name);
            case "brand":
                return deleteBrand(name);
            case "merchant":
                return deleteMerchant(name);
            case "warehouse":
                return deleteWarehouse(name);
            case "item":
                return deleteItem(name);
            default:
                return "暂不支持删除「" + domain + "」。";
        }
    }

    private String trim(String s) {
        return StrUtil.nullToEmpty(s).trim();
    }

}
