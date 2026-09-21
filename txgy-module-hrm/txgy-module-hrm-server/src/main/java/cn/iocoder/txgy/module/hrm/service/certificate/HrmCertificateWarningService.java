package cn.iocoder.txgy.module.hrm.service.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningHandleReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.warning.HrmCertificateWarningRespVO;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * HRM 证件预警 Service 接口
 *
 * 预警记录是可重建的派生数据：每日扫描按「证件 + 预警类型 + 预警日期」去重生成，
 * 通知失败只记录失败原因并允许重试，不影响证件台账这一业务事实。
 *
 * @author OPENLAB BS
 */
public interface HrmCertificateWarningService {

    /**
     * 通知失败的最大重试次数
     */
    int MAX_NOTIFY_RETRY_COUNT = 3;

    /**
     * 每日扫描：生成证件到期与复审预警记录
     *
     * @param today 扫描基准日期
     * @return 新生成的预警记录数量
     */
    int scanAndGenerateWarnings(LocalDate today);

    /**
     * 获得证件预警分页
     *
     * @param reqVO 分页查询
     * @return 证件预警分页
     */
    PageResult<HrmCertificateWarningRespVO> getCertificateWarningPage(HrmCertificateWarningPageReqVO reqVO);

    /**
     * 处理证件预警：标记已处理、已忽略，或将已处理/已忽略重新启用为待处理
     *
     * @param reqVO 处理信息
     */
    void handleCertificateWarning(HrmCertificateWarningHandleReqVO reqVO);

    /**
     * 获得待处理预警数量
     *
     * @return 待处理预警数量
     */
    Long getPendingWarningCount();

    /**
     * 获得证件对应的待处理预警数量 Map，便于列表批量填充
     *
     * @param certificateIds 证件台账编号集合
     * @return 证件台账编号 -> 待处理预警数量
     */
    Map<Long, Long> getPendingWarningCountMap(Collection<Long> certificateIds);

    /**
     * 发送待通知的预警，失败时记录原因并累计重试次数
     *
     * @param today 发送基准日期
     * @return 通知成功的数量
     */
    int notifyPendingWarnings(LocalDate today);

}
