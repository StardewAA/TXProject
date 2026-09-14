package cn.iocoder.txgy.module.bidding.service.ruleconfig;

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.meta.vo.DictItemRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingDictDataDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig.BiddingDictDataMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 字典下发口径测试。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§1.1（字典读取）；
 * 用户硬约束"枚举与阈值一律动态配置"。
 * <p>为什么这样写：本次修的是"受支持类型只认代码枚举"造成的漏发——已种字典的纯字面量类型
 * （cert_type / holder_type / review_result）取不到，而真正未知的类型仍必须显式报错。
 * 三条用例分别锁定：DB-only 类型可下发、未知类型仍报 1060108008、不带 types 时包含 DB-only 类型。
 * <p>业务目标：字典"是否受支持"以数据为准——插数据即下发，不必为每种字典补枚举；
 * 同时不放过前端拼错类型名的调用（静默返回空下拉最难排查）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetaDictServiceImplTest {

    @Mock
    private BiddingDictDataMapper dictDataMapper;

    @InjectMocks
    private MetaDictServiceImpl metaDictService;

    private static BiddingDictDataDO row(String type, String value, String label) {
        BiddingDictDataDO row = new BiddingDictDataDO();
        row.setDictType(type);
        row.setDictValue(value);
        row.setDictLabel(label);
        row.setColorType("success");
        row.setTerminal(Boolean.FALSE);
        row.setSort(1);
        row.setEnabled(Boolean.TRUE);
        return row;
    }

    @Test
    void getDicts_shouldServeDbOnlyType() {
        given(dictDataMapper.selectEnabledTypes(0L)).willReturn(List.of("cert_type"));
        given(dictDataMapper.selectEnabledByTypes(eq(0L), anyList()))
                .willReturn(List.of(row("cert_type", "ADMIN_LICENSE", "营业执照")));

        Map<String, List<DictItemRespVO>> result = metaDictService.getDicts(List.of("cert_type"));

        assertEquals(1, result.get("cert_type").size());
        assertEquals("营业执照", result.get("cert_type").get(0).getLabel());
    }

    @Test
    void getDicts_shouldRejectUnknownType() {
        given(dictDataMapper.selectEnabledTypes(0L)).willReturn(List.of("cert_type"));

        ServiceException ex = assertThrows(ServiceException.class,
                () -> metaDictService.getDicts(List.of("no_such_type")));

        assertEquals(ErrorCodeConstants.DICT_TYPE_NOT_SUPPORTED.getCode(), ex.getCode());
    }

    @Test
    void getDicts_withoutTypes_shouldIncludeDbOnlyType() {
        given(dictDataMapper.selectEnabledTypes(0L)).willReturn(List.of("cert_type"));
        given(dictDataMapper.selectEnabledByTypes(eq(0L), anyList())).willReturn(List.of());

        Map<String, List<DictItemRespVO>> result = metaDictService.getDicts(null);

        assertTrue(result.containsKey("cert_type"), "不带 types 时应包含字典表里已配置的纯字面量类型");
        assertTrue(result.containsKey("cert_status"), "枚举类型仍应在默认返回里");
    }
}
