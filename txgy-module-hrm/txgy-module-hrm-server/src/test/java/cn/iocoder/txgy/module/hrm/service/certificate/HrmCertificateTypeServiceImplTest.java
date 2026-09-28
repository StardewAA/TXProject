package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.certificate.HrmCertificateTypeMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.txgy.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.txgy.framework.test.core.util.RandomUtils.randomString;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_CODE_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_DISABLED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_NAME_DUPLICATE;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_NOT_EXISTS;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_SPECIAL_OPERATION_REVIEW_REQUIRED;
import static cn.iocoder.txgy.module.hrm.enums.ErrorCodeConstants.CERTIFICATE_TYPE_USED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HrmCertificateTypeServiceImpl} 的单元测试类
 *
 * @author OPENLAB BS
 */
@Import(HrmCertificateTypeServiceImpl.class)
public class HrmCertificateTypeServiceImplTest extends BaseDbUnitTest {

    @Resource
    private HrmCertificateTypeServiceImpl certificateTypeService;
    @Resource
    private HrmCertificateTypeMapper certificateTypeMapper;
    @Resource
    private HrmEmployeeCertificateMapper certificateMapper;

    @Test
    public void testCreateCertificateType_success() {
        // 准备参数
        HrmCertificateTypeSaveReqVO reqVO = buildReqVO(null, "特种作业操作证", "SPECIAL_OPERATION");

        // 调用
        Long id = certificateTypeService.createCertificateType(reqVO);

        // 断言
        assertNotNull(id);
        HrmCertificateTypeDO certificateType = certificateTypeMapper.selectById(id);
        assertPojoEquals(reqVO, certificateType, "id");
        assertEquals(id, certificateType.getId());
    }

    @Test
    public void testCreateCertificateType_nameDuplicate() {
        // mock 数据
        HrmCertificateTypeDO dbRecord = randomPojo(HrmCertificateTypeDO.class, o -> o.setId(null));
        certificateTypeMapper.insert(dbRecord);
        // 准备参数：同名
        HrmCertificateTypeSaveReqVO reqVO = buildReqVO(null, dbRecord.getName(), randomString());

        // 调用，并断言异常
        assertServiceException(() -> certificateTypeService.createCertificateType(reqVO),
                CERTIFICATE_TYPE_NAME_DUPLICATE);
    }

    @Test
    public void testCreateCertificateType_codeDuplicate() {
        // mock 数据
        HrmCertificateTypeDO dbRecord = randomPojo(HrmCertificateTypeDO.class,
                o -> o.setId(null).setCode("SPECIAL_OPERATION"));
        certificateTypeMapper.insert(dbRecord);
        // 准备参数：编码重复
        HrmCertificateTypeSaveReqVO reqVO = buildReqVO(null, randomString(), dbRecord.getCode());

        // 调用，并断言异常
        assertServiceException(() -> certificateTypeService.createCertificateType(reqVO),
                CERTIFICATE_TYPE_CODE_DUPLICATE);
    }

    @Test
    public void testCreateCertificateType_specialOperationWithoutReview() {
        // 准备参数：特种作业但未开启复审
        HrmCertificateTypeSaveReqVO reqVO = buildReqVO(null, randomString(), randomString())
                .setSpecialOperation(true).setNeedReview(false).setReviewCycleMonths(null);

        // 调用，并断言异常
        assertServiceException(() -> certificateTypeService.createCertificateType(reqVO),
                CERTIFICATE_TYPE_SPECIAL_OPERATION_REVIEW_REQUIRED);
    }

    @Test
    public void testCreateCertificateType_specialOperationWithoutReviewCycle() {
        // 准备参数：特种作业且开启复审，但未配置复审周期
        HrmCertificateTypeSaveReqVO reqVO = buildReqVO(null, randomString(), randomString())
                .setSpecialOperation(true).setNeedReview(true).setReviewCycleMonths(null);

        // 调用，并断言异常
        assertServiceException(() -> certificateTypeService.createCertificateType(reqVO),
                CERTIFICATE_TYPE_SPECIAL_OPERATION_REVIEW_REQUIRED);
    }

    @Test
    public void testUpdateCertificateType_success() {
        // mock 数据
        HrmCertificateTypeDO dbRecord = randomPojo(HrmCertificateTypeDO.class,
                o -> o.setId(null).setStatus(CommonStatusEnum.ENABLE.getStatus()));
        certificateTypeMapper.insert(dbRecord);
        // 准备参数
        HrmCertificateTypeSaveReqVO reqVO = buildReqVO(dbRecord.getId(), "更新后的类型", "UPDATED_CODE");

        // 调用
        certificateTypeService.updateCertificateType(reqVO);

        // 断言
        HrmCertificateTypeDO certificateType = certificateTypeMapper.selectById(dbRecord.getId());
        assertPojoEquals(reqVO, certificateType);
    }

    @Test
    public void testUpdateCertificateType_notExists() {
        // 准备参数
        HrmCertificateTypeSaveReqVO reqVO = buildReqVO(randomLongId(), randomString(), randomString());

        // 调用，并断言异常
        assertServiceException(() -> certificateTypeService.updateCertificateType(reqVO),
                CERTIFICATE_TYPE_NOT_EXISTS);
    }

    @Test
    public void testDeleteCertificateType_success() {
        // mock 数据
        HrmCertificateTypeDO dbRecord = randomPojo(HrmCertificateTypeDO.class, o -> o.setId(null));
        certificateTypeMapper.insert(dbRecord);

        // 调用
        certificateTypeService.deleteCertificateType(dbRecord.getId());

        // 断言
        assertNull(certificateTypeMapper.selectById(dbRecord.getId()));
    }

    @Test
    public void testDeleteCertificateType_notExists() {
        // 调用，并断言异常
        assertServiceException(() -> certificateTypeService.deleteCertificateType(randomLongId()),
                CERTIFICATE_TYPE_NOT_EXISTS);
    }

    @Test
    public void testDeleteCertificateType_usedByLedger() {
        // mock 数据：证件类型已被证件台账引用
        HrmCertificateTypeDO dbRecord = randomPojo(HrmCertificateTypeDO.class, o -> o.setId(null));
        certificateTypeMapper.insert(dbRecord);
        certificateMapper.insert(randomPojo(HrmEmployeeCertificateDO.class,
                o -> o.setId(null).setTypeId(dbRecord.getId())));

        // 调用，并断言异常
        assertServiceException(() -> certificateTypeService.deleteCertificateType(dbRecord.getId()),
                CERTIFICATE_TYPE_USED);
    }

    @Test
    public void testValidateCertificateTypeEnabled_disabled() {
        // mock 数据：停用的证件类型
        HrmCertificateTypeDO dbRecord = randomPojo(HrmCertificateTypeDO.class,
                o -> o.setId(null).setStatus(CommonStatusEnum.DISABLE.getStatus()));
        certificateTypeMapper.insert(dbRecord);

        // 调用，并断言异常
        assertServiceException(() -> certificateTypeService.validateCertificateTypeEnabled(dbRecord.getId()),
                CERTIFICATE_TYPE_DISABLED, dbRecord.getName());
    }

    @Test
    public void testGetCertificateTypeList_byStatus() {
        // mock 数据
        HrmCertificateTypeDO enabled = randomPojo(HrmCertificateTypeDO.class,
                o -> o.setId(null).setStatus(CommonStatusEnum.ENABLE.getStatus()).setSort(10));
        certificateTypeMapper.insert(enabled);
        certificateTypeMapper.insert(randomPojo(HrmCertificateTypeDO.class,
                o -> o.setId(null).setStatus(CommonStatusEnum.DISABLE.getStatus()).setSort(1)));

        // 调用
        List<HrmCertificateTypeDO> result = certificateTypeService.getCertificateTypeList(
                CommonStatusEnum.ENABLE.getStatus());

        // 断言
        assertEquals(1, result.size());
        assertEquals(enabled.getId(), result.get(0).getId());
    }

    @Test
    public void testGetCertificateTypePage_byName() {
        // mock 数据
        HrmCertificateTypeDO dbRecord = randomPojo(HrmCertificateTypeDO.class,
                o -> o.setId(null).setName("低压电工作业操作证"));
        certificateTypeMapper.insert(dbRecord);
        certificateTypeMapper.insert(randomPojo(HrmCertificateTypeDO.class,
                o -> o.setId(null).setName("高处作业操作证")));
        // 准备参数
        HrmCertificateTypePageReqVO reqVO = new HrmCertificateTypePageReqVO();
        reqVO.setName("电工作业");
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);

        // 调用
        PageResult<HrmCertificateTypeDO> pageResult = certificateTypeService.getCertificateTypePage(reqVO);

        // 断言
        assertEquals(1, pageResult.getTotal());
        assertEquals(dbRecord.getId(), pageResult.getList().get(0).getId());
    }

    @Test
    public void testGetCertificateTypeMap() {
        // mock 数据
        HrmCertificateTypeDO dbRecord = randomPojo(HrmCertificateTypeDO.class, o -> o.setId(null));
        certificateTypeMapper.insert(dbRecord);

        // 调用
        Map<Long, HrmCertificateTypeDO> result = certificateTypeService.getCertificateTypeMap(
                Collections.singleton(dbRecord.getId()));

        // 断言
        assertEquals(1, result.size());
        assertEquals(dbRecord.getName(), result.get(dbRecord.getId()).getName());
        // 空集合返回空 Map，避免下游判空
        assertTrue(certificateTypeService.getCertificateTypeMap(Collections.emptyList()).isEmpty());
    }

    /**
     * 构造一个合法的证件类型请求，默认非特种作业、不需要复审，避免随机值触发特种作业校验
     */
    private HrmCertificateTypeSaveReqVO buildReqVO(Long id, String name, String code) {
        HrmCertificateTypeSaveReqVO reqVO = randomPojo(HrmCertificateTypeSaveReqVO.class, o -> o.setId(id));
        reqVO.setName(name);
        reqVO.setCode(code);
        reqVO.setSpecialOperation(false);
        reqVO.setNeedReview(false);
        reqVO.setReviewCycleMonths(null);
        reqVO.setAdvanceDays(30);
        reqVO.setRequireAttachment(false);
        reqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        reqVO.setSort(10);
        return reqVO;
    }

}
