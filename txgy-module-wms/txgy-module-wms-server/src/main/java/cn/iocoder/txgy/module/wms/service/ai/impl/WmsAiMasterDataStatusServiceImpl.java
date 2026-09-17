package cn.iocoder.txgy.module.wms.service.ai.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemCategoryDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemCategoryMapper;
import cn.iocoder.txgy.module.wms.service.ai.WmsAiMasterDataStatusService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 切换主数据状态服务实现（目前仅支持商品分类的启用/停用）。
 */
@Service
@Slf4j
public class WmsAiMasterDataStatusServiceImpl implements WmsAiMasterDataStatusService {

    @Resource
    private WmsItemCategoryMapper itemCategoryMapper;

    @Override
    public String changeStatus(String type, JSONObject intent, boolean enable) {
        String name = trim(intent == null ? null : intent.getStr("name"));
        if (StrUtil.isBlank(name)) {
            return "请告诉我要" + (enable ? "启用" : "停用") + "的分类名称，例如："
                    + (enable ? "启用分类 休闲食品" : "停用分类 休闲食品") + "。";
        }
        WmsItemCategoryDO category = itemCategoryMapper.selectOne(
                new LambdaQueryWrapper<WmsItemCategoryDO>()
                        .eq(WmsItemCategoryDO::getName, name).last("LIMIT 1"));
        if (category == null) {
            return "没有找到商品分类「" + name + "」，请确认名称是否正确。你也可以问「商品分类有哪些」查看现有分类。";
        }
        Integer targetStatus = enable ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus();
        if (category.getStatus() != null && category.getStatus().equals(targetStatus)) {
            return "分类「" + category.getName() + "」当前已经是" + (enable ? "启用" : "停用") + "状态。";
        }
        category.setStatus(targetStatus);
        itemCategoryMapper.updateById(category);
        log.info("[ai-status] 分类「{}」状态切换为 {}", category.getName(), enable ? "启用" : "停用");
        return "已将分类「" + category.getName() + "」" + (enable ? "启用" : "停用") + "。";
    }

    private String trim(String s) {
        return StrUtil.nullToEmpty(s).trim();
    }

}
