package cn.iocoder.txgy.module.bidding.service.subject;
// [依据] 主体档案规则测试：USCC 按 GB 32100-2015 校验（docs/法规依据/00-索引）；
//       完善度=必填清单全齐（表89 法人基本信息必填项；电子招标投标办法第24条“如实递交并经验证”）。

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectProfileDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.subject.SubjectProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主体信息档案服务测试（Mockito）：USCC 强校验 / 完善度核算 / 一租户一类型一档案。
 */
class SubjectProfileServiceImplTest {

    private SubjectProfileMapper profileMapper;
    private SubjectProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        profileMapper = mock(SubjectProfileMapper.class);
        service = new SubjectProfileServiceImpl();
        ReflectionTestUtils.setField(service, "subjectProfileMapper", profileMapper);
    }

    @Test
    void save_newProfile_completeFlagTrue() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        when(profileMapper.insert(any(SubjectProfileDO.class))).thenAnswer(inv -> {
            inv.getArgument(0, SubjectProfileDO.class).setId(1L);
            return 1;
        });

        Long id = service.saveOwnProfile(completeReq());

        assertEquals(1L, id);
        ArgumentCaptor<SubjectProfileDO> captor = ArgumentCaptor.forClass(SubjectProfileDO.class);
        verify(profileMapper).insert(captor.capture());
        SubjectProfileDO saved = captor.getValue();
        assertTrue(saved.getCompleteFlag(), "必填全齐应置 complete=true");
        assertEquals("156", saved.getRegCapCurrency(), "默认币种 156=人民币（GB/T 12406-2008）");
    }

    @Test
    void save_missingContact_keepsIncomplete() {
        // 授权≠完善：缺联系人 → complete=false，且完整时间不落
        when(profileMapper.selectOne(any())).thenReturn(null);
        when(profileMapper.insert(any(SubjectProfileDO.class))).thenAnswer(inv -> 1);
        SubjectProfileSaveReqVO req = completeReq();
        req.setContactName("");

        service.saveOwnProfile(req);

        ArgumentCaptor<SubjectProfileDO> captor = ArgumentCaptor.forClass(SubjectProfileDO.class);
        verify(profileMapper).insert(captor.capture());
        assertFalse(captor.getValue().getCompleteFlag());
        assertNull(captor.getValue().getCompleteTime());
    }

    @Test
    void save_rejectsInvalidUsccChecksum() {
        // GB 32100-2015 校验位不符 → 106_035
        when(profileMapper.selectOne(any())).thenReturn(null);
        SubjectProfileSaveReqVO req = completeReq();
        req.setUscc("91110108MA01ABCDEF"); // 校验位不符的 18 位
        ServiceException e = assertThrows(ServiceException.class, () -> service.saveOwnProfile(req));
        assertEquals(Integer.valueOf(1_060_106_035), e.getCode());
        verify(profileMapper, never()).insert(any(SubjectProfileDO.class));
    }

    @Test
    void save_rejectsUnsupportedType() {
        // AGENCY 预留未落地 → 106_030
        SubjectProfileSaveReqVO req = completeReq();
        req.setSubjectType("AGENCY");
        ServiceException e = assertThrows(ServiceException.class, () -> service.saveOwnProfile(req));
        assertEquals(Integer.valueOf(1_060_106_030), e.getCode());
    }

    @Test
    void getOwnProfile_exposesMissingRequired() {
        SubjectProfileDO profile = doOf();
        profile.setRegionCode(null); // 缺行政区划
        when(profileMapper.selectOne(any())).thenReturn(profile);

        SubjectProfileRespVO resp = service.getOwnProfile("BIDDER");

        assertFalse(resp.getComplete());
        assertTrue(resp.getMissingRequiredCodes().contains("regionCode"));
    }

    // ========== 工具 ==========

    private SubjectProfileSaveReqVO completeReq() {
        SubjectProfileSaveReqVO req = new SubjectProfileSaveReqVO();
        req.setSubjectType("BIDDER");
        req.setSubjectName("陕西某保温防腐工程有限公司");
        req.setUscc(validUscc());
        req.setLegalPersonName("张三");
        req.setLegalPersonTypeCode("1");
        req.setRegionCode("610100");
        req.setContactName("李四");
        req.setContactPhone("13800000000");
        return req;
    }

    /** 构造一个校验位正确的 18 位统一社会信用代码（GB 32100-2015 算法） */
    private String validUscc() {
        String prefix = "91110108MA01ABCDE";
        int sum = 0;
        String charset = "0123456789ABCDEFGHJKLMNPQRTUWXY";
        int[] weights = {1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28};
        for (int i = 0; i < 17; i++) {
            sum += charset.indexOf(prefix.charAt(i)) * weights[i];
        }
        return prefix + charset.charAt(sum % 31);
    }

    private SubjectProfileDO doOf() {
        SubjectProfileDO p = new SubjectProfileDO();
        p.setId(1L);
        p.setSubjectType("BIDDER");
        p.setSubjectName("陕西某保温防腐工程有限公司");
        p.setUscc(validUscc());
        p.setLegalPersonName("张三");
        p.setLegalPersonTypeCode("1");
        p.setRegionCode("610100");
        p.setContactName("李四");
        p.setContactPhone("13800000000");
        return p;
    }
}
