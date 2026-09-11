package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.config.CrmAfterSaleConfigSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleConfigDO;
import cn.iocoder.txgy.module.crm.dal.mysql.aftersale.CrmAfterSaleConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 售后设置 Service 实现类（单行配置 id = 1）
 *
 * @author txgycloud
 */
@Service
@Validated
public class CrmAfterSaleConfigServiceImpl implements CrmAfterSaleConfigService {

    public static final Long CONFIG_ID = 1L;

    @Resource
    private CrmAfterSaleConfigMapper crmAfterSaleConfigMapper;

    @Override
    public CrmAfterSaleConfigDO getConfig() {
        CrmAfterSaleConfigDO config = crmAfterSaleConfigMapper.selectById(CONFIG_ID);
        if (config != null) {
            return config;
        }
        // 不存在时返回系统默认值（不落库，保持表干净）
        return CrmAfterSaleConfigDO.builder().id(CONFIG_ID)
                .warrantyWarnDays(30)
                .autoVisitEnabled(false)
                .autoVisitCycleType(1)
                .autoVisitCycleValue(0)
                .taskRemindDays(3)
                .taskOverdueDays(3)
                .ticketOverdueDays(7)
                .build();
    }

    @Override
    public void updateConfig(CrmAfterSaleConfigSaveReqVO updateReqVO) {
        // 单行配置：不存在则插入，存在则更新
        CrmAfterSaleConfigDO oldConfig = crmAfterSaleConfigMapper.selectById(CONFIG_ID);
        CrmAfterSaleConfigDO config = BeanUtils.toBean(updateReqVO, CrmAfterSaleConfigDO.class);
        if (oldConfig == null) {
            config.setId(CONFIG_ID);
            crmAfterSaleConfigMapper.insert(config);
        } else {
            config.setId(CONFIG_ID);
            crmAfterSaleConfigMapper.updateById(config);
        }
    }

}
