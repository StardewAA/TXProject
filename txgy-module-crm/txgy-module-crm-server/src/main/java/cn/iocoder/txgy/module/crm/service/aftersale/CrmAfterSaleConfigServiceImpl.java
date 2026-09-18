package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.config.CrmAfterSaleConfigSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleConfigDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 售后设置 Service 实现类（按租户单行配置）
 *
 * @author txgycloud
 */
@Service
@Validated
public class CrmAfterSaleConfigServiceImpl implements CrmAfterSaleConfigService {

    @Resource
    private CrmAfterSaleConfigMapper crmAfterSaleConfigMapper;

    @Override
    public CrmAfterSaleConfigDO getConfig() {
        CrmAfterSaleConfigDO config = selectFirstConfig();
        if (config != null) {
            return config;
        }
        // 不存在时返回系统默认值（不落库，保持表干净）
        return CrmAfterSaleConfigDO.builder()
                .warrantyWarnDays(30)
                .autoVisitEnabled(false)
                .autoVisitCycleType(1)
                .autoVisitCycleValue(0)
                .taskRemindDays(3)
                .taskOverdueDays(3)
                // 默认不允许提前提交回访记录（保持「未到计划回访日期禁止提交」的硬时间锁）
                .allowEarlyVisit(false)
                .ticketOverdueDays(7)
                .build();
    }

    @Override
    public void updateConfig(CrmAfterSaleConfigSaveReqVO updateReqVO) {
        // 单行配置：当前租户已有则更新，没有则插入
        // 说明：不能固定 id = 1 —— 主键全局唯一，第二个租户插入会主键冲突（Duplicate entry '1'）；
        //      这里按「当前租户的第一条」定位（租户隔离由多租户插件自动保证），插入使用自增主键
        CrmAfterSaleConfigDO oldConfig = selectFirstConfig();
        CrmAfterSaleConfigDO config = BeanUtils.toBean(updateReqVO, CrmAfterSaleConfigDO.class);
        if (oldConfig == null) {
            crmAfterSaleConfigMapper.insert(config);
            return;
        }
        config.setId(oldConfig.getId());
        crmAfterSaleConfigMapper.updateById(config);
    }

    /**
     * 取当前租户下的单行配置
     *
     * <p>多租户插件会自动追加 tenant_id 条件，因此不能按固定主键 id = 1 读取
     *
     * @return 当前租户的配置；不存在时返回 null
     */
    private CrmAfterSaleConfigDO selectFirstConfig() {
        List<CrmAfterSaleConfigDO> configs = crmAfterSaleConfigMapper.selectList(
                new LambdaQueryWrapperX<CrmAfterSaleConfigDO>().orderByAsc(CrmAfterSaleConfigDO::getId));
        return CollUtil.isEmpty(configs) ? null : configs.get(0);
    }

}
