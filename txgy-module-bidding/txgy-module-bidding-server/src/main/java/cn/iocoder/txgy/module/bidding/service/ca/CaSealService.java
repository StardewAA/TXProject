package cn.iocoder.txgy.module.bidding.service.ca;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.SealCallbackReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.SealDocumentRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ca.CaSealDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 文件 CA 电子签章服务（测试配置本地真实签名；真实配置由官方 SDK 接管）。 */
public interface CaSealService {

    /** 对文件计算 SM3、生成真实 SM2 签名并产出已签章文件（每次签章直接落库 CA 签章台账）。 */
    SealDocumentRespVO sealDocument(MultipartFile file, String businessCode, String callbackUrl);

    /** 批量文件签章（多册投标文件统一盖电子章；每份逐页盖章 + 各自落库台账；批内整体一个事务）。 */
    List<SealDocumentRespVO> sealDocumentBatch(List<MultipartFile> files, String businessCode, String callbackUrl);

    /** 接收本地/真实 H5 签章完成回调并回写投标流程留痕与签章台账。 */
    boolean saveCallback(SealCallbackReqVO reqVO);

    /** 验签（真实 SM2 验签：对台账记录的 SM3 指纹验签名），通过/不通过回写 verifyStatus。 */
    CaSealRespVO verifySeal(Long id);

    /** 按主键查询签章记录。 */
    CaSealDO get(Long id);

    /** 上传后检测文件是否已 CA 认证（按原文件 fileId 匹配台账，sha256 兜底）。 */
    CaSealCheckRespVO checkSealAuth(Long fileId, String sha256);

    /** 签章统计（演示仪表卡：总数/PASS/FAIL/UNVERIFIED）。 */
    CaSealStatsRespVO stats();

    /** 生成演示签章数据（真实 SM2 签名 + PDF 红章，直接落库台账；count 1~20，默认 5）。 */
    List<CaSealRespVO> demoSeed(Integer count);

    /** 生成演示「被篡改」签章数据（内容替换 → 原签名失效，验签必 FAIL，展示检出能力；count 1~20，默认 2）。 */
    List<CaSealRespVO> demoTamper(Integer count);

    /** 清空演示签章数据（含篡改样例），演示后还原台账；返回删除条数。 */
    Long demoClear();

    /** 签章台账分页。 */
    PageResult<CaSealDO> page(CaSealPageReqVO reqVO);
}
