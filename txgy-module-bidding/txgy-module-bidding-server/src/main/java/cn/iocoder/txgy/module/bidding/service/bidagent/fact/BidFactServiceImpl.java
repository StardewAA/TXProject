package cn.iocoder.txgy.module.bidding.service.bidagent.fact;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidEnterpriseFactDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidEnterpriseFactMapper;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidFactRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;

/**
 * 企业事实服务实现
 */
@Service
public class BidFactServiceImpl implements BidFactService {

    @Resource
    private BidEnterpriseFactMapper factMapper;

    @Override
    public List<BidFactRespDTO> getFacts(Long tenantId, List<String> types) {
        if (tenantId == null) {
            return List.of();
        }
        return TenantUtils.execute(tenantId, () -> {
            List<BidEnterpriseFactDO> list = CollUtil.isEmpty(types)
                    ? factMapper.selectListByTypes(null)
                    : factMapper.selectListByTypes(types);
            return convertList(list, this::toDTO);
        });
    }

    private BidFactRespDTO toDTO(BidEnterpriseFactDO fact) {
        BidFactRespDTO dto = new BidFactRespDTO();
        dto.setFactId(fact.getId() == null ? null : fact.getId().toString());
        dto.setType(fact.getType());
        dto.setName(fact.getName());
        dto.setValue(fact.getValue());
        dto.setUnit(fact.getUnit());
        dto.setValidUntil(fact.getValidUntil() == null ? null : fact.getValidUntil().toString());
        dto.setSourceDocName(fact.getSourceDocName());
        dto.setSourceBlockIds(split(fact.getSourceBlockIds()));
        return dto;
    }

    private static List<String> split(String joined) {
        if (StrUtil.isBlank(joined)) {
            return List.of();
        }
        return new ArrayList<>(List.of(joined.split(",")));
    }
}
