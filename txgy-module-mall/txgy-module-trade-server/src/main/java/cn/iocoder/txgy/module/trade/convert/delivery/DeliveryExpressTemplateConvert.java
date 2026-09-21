package cn.iocoder.txgy.module.trade.convert.delivery;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.collection.CollectionUtils;
import cn.iocoder.txgy.framework.ip.core.enums.AreaTypeEnum;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.module.trade.controller.admin.delivery.vo.expresstemplate.*;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateChargeDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressTemplateFreeDO;
import cn.iocoder.txgy.module.trade.service.delivery.bo.DeliveryExpressTemplateRespBO;
import com.google.common.collect.Maps;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMultiMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.findFirst;

@Mapper
public interface DeliveryExpressTemplateConvert {

    DeliveryExpressTemplateConvert INSTANCE = Mappers.getMapper(DeliveryExpressTemplateConvert.class);

    // ========== Template ==========

    DeliveryExpressTemplateDO convert(DeliveryExpressTemplateCreateReqVO bean);

    DeliveryExpressTemplateDO convert(DeliveryExpressTemplateUpdateReqVO bean);

    DeliveryExpressTemplateRespVO convert(DeliveryExpressTemplateDO bean);

    DeliveryExpressTemplateDetailRespVO convert2(DeliveryExpressTemplateDO bean);

    List<DeliveryExpressTemplateRespVO> convertList(List<DeliveryExpressTemplateDO> list);

    List<DeliveryExpressTemplateSimpleRespVO> convertList1(List<DeliveryExpressTemplateDO> list);

    PageResult<DeliveryExpressTemplateRespVO> convertPage(PageResult<DeliveryExpressTemplateDO> page);

    default DeliveryExpressTemplateDetailRespVO convert(DeliveryExpressTemplateDO bean,
                                                        List<DeliveryExpressTemplateChargeDO> chargeList,
                                                        List<DeliveryExpressTemplateFreeDO> freeList) {
        DeliveryExpressTemplateDetailRespVO respVO = convert2(bean);
        respVO.setCharges(convertTemplateChargeList(chargeList));
        respVO.setFrees(convertTemplateFreeList(freeList));
        return respVO;
    }

    // ========== Template Charge ==========

    DeliveryExpressTemplateChargeDO convertTemplateCharge(Long templateId, Integer chargeMode, DeliveryExpressTemplateChargeBaseVO vo);

    DeliveryExpressTemplateRespBO.Charge convertTemplateCharge(DeliveryExpressTemplateChargeDO bean);

    default List<DeliveryExpressTemplateChargeDO> convertTemplateChargeList(Long templateId, Integer chargeMode, List<DeliveryExpressTemplateChargeBaseVO> list) {
        return CollectionUtils.convertList(list, vo -> convertTemplateCharge(templateId, chargeMode, vo));
    }

    // ========== Template Free ==========

    DeliveryExpressTemplateFreeDO convertTemplateFree(Long templateId, DeliveryExpressTemplateFreeBaseVO vo);

    DeliveryExpressTemplateRespBO.Free convertTemplateFree(DeliveryExpressTemplateFreeDO bean);

    List<DeliveryExpressTemplateChargeBaseVO> convertTemplateChargeList(List<DeliveryExpressTemplateChargeDO> list);

    List<DeliveryExpressTemplateFreeBaseVO> convertTemplateFreeList(List<DeliveryExpressTemplateFreeDO> list);

    default List<DeliveryExpressTemplateFreeDO> convertTemplateFreeList(Long templateId, List<DeliveryExpressTemplateFreeBaseVO> list) {
        return CollectionUtils.convertList(list, vo -> convertTemplateFree(templateId, vo));
    }

    default Map<Long, DeliveryExpressTemplateRespBO> convertMap(Integer areaId, List<DeliveryExpressTemplateDO> templateList,
                                                                List<DeliveryExpressTemplateChargeDO> chargeList,
                                                                List<DeliveryExpressTemplateFreeDO> freeList) {
        Map<Long, List<DeliveryExpressTemplateChargeDO>> templateIdChargeMap = convertMultiMap(chargeList,
                DeliveryExpressTemplateChargeDO::getTemplateId);
        Map<Long, List<DeliveryExpressTemplateFreeDO>> templateIdFreeMap = convertMultiMap(freeList,
                DeliveryExpressTemplateFreeDO::getTemplateId);
        // 组合运费模板配置 RespBO
        Map<Long, DeliveryExpressTemplateRespBO> result = Maps.newHashMapWithExpectedSize(templateList.size());
        templateList.forEach(template -> {
            DeliveryExpressTemplateRespBO bo = new DeliveryExpressTemplateRespBO()
                    .setChargeMode(template.getChargeMode())
                    .setCharge(convertTemplateCharge(findAreaConfig(templateIdChargeMap.get(template.getId()),
                            DeliveryExpressTemplateChargeDO::getAreaIds, areaId)))
                    .setFree(convertTemplateFree(findAreaConfig(templateIdFreeMap.get(template.getId()),
                            DeliveryExpressTemplateFreeDO::getAreaIds, areaId)));
            if (bo.getCharge() != null || bo.getFree() != null) {
                result.put(template.getId(), bo);
            }
        });
        return result;
    }

    /**
     * 运费模板通常按省或市配置，而会员地址保存到区县。按“区县、城市、省份、国家”
     * 由具体到宽泛逐级匹配，避免区县编号与模板区域编号不完全相等时无法结算。
     */
    default <T> T findAreaConfig(List<T> configs, Function<T, List<Integer>> areaIdsGetter, Integer areaId) {
        if (configs == null) {
            return null;
        }
        Integer[] candidateAreaIds = {
                areaId,
                AreaUtils.getParentIdByType(areaId, AreaTypeEnum.CITY),
                AreaUtils.getParentIdByType(areaId, AreaTypeEnum.PROVINCE),
                AreaUtils.getParentIdByType(areaId, AreaTypeEnum.COUNTRY)
        };
        for (Integer candidateAreaId : candidateAreaIds) {
            if (candidateAreaId == null) {
                continue;
            }
            T config = findFirst(configs, item -> areaIdsGetter.apply(item).contains(candidateAreaId));
            if (config != null) {
                return config;
            }
        }
        return null;
    }

}
