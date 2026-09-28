package cn.iocoder.txgy.module.crm.service.aftersale;

import cn.iocoder.txgy.module.crm.controller.admin.aftersale.vo.config.CrmAfterSaleConfigSaveReqVO;
import cn.iocoder.txgy.module.crm.dal.dataobject.aftersale.CrmAfterSaleConfigDO;
import jakarta.validation.Valid;

/**
 * 售后设置 Service 接口
 *
 * @author txgycloud
 */
public interface CrmAfterSaleConfigService {

    /**
     * 获得售后设置；若不存在则返回系统默认值（不落库）
     *
     * @return 售后设置
     */
    CrmAfterSaleConfigDO getConfig();

    /**
     * 更新售后设置（单行，不存在则创建）
     *
     * @param updateReqVO 更新信息
     */
    void updateConfig(@Valid CrmAfterSaleConfigSaveReqVO updateReqVO);

}
