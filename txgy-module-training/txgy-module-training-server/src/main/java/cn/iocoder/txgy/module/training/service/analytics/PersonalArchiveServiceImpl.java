package cn.iocoder.txgy.module.training.service.analytics;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * 92 个人学习档案 Service 实现（框架骨架）
 *
 * @author 云培训学院课题小组
 */
@Service
@Validated
public class PersonalArchiveServiceImpl implements PersonalArchiveService {

    @Override
    public Map<String, Object> getOverview(Long userId) {
        // TODO[模块九-个人档案] 汇聚课程学时明细、考试记录、证书、VR实操记录、
        //  人才成长进度与晋升条件缺口（依赖模块四/五数据完成后接入）
        Map<String, Object> overview = new HashMap<>();
        overview.put("userId", userId);
        overview.put("developing", true);
        return overview;
    }

    @Override
    public Boolean remindCertExpire(Long userId) {
        // TODO[模块九-证书到期] 识别即将到期、需要复审的证书，向学员推送到期提醒消息
        return Boolean.TRUE;
    }

}
