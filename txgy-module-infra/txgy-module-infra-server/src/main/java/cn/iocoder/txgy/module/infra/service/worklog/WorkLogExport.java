package cn.iocoder.txgy.module.infra.service.worklog;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.infra.controller.admin.worklog.vo.WorkLogRespVO;
import cn.iocoder.txgy.module.infra.dal.dataobject.worklog.WorkLogDO;
import java.util.*;

/** XLSX cells are limited to 32,767 UTF-16 units; preserve large details in numbered rows. */
public final class WorkLogExport {
    private WorkLogExport() { }
    public static List<WorkLogRespVO> rows(List<WorkLogDO> logs) {
        List<WorkLogRespVO> result = new ArrayList<>();
        for (WorkLogDO log : logs) {
            List<String> requests = parts(log.getRequestParams());
            List<String> changes = parts(log.getChanges());
            int count = Math.max(requests.size(), changes.size());
            for (int part=0; part<count; part++) {
                WorkLogRespVO row = BeanUtils.toBean(log, WorkLogRespVO.class);
                row.setContentPart((part+1) + "/" + count);
                row.setRequestParams(part < requests.size() ? requests.get(part) : "");
                row.setChanges(part < changes.size() ? changes.get(part) : "");
                result.add(row);
            }
        }
        return result;
    }
    private static List<String> parts(String text) {
        if (text == null || text.isEmpty()) return List.of("");
        List<String> parts = new ArrayList<>();
        for (int start=0; start<text.length();) {
            int end=Math.min(start+30000,text.length());
            if (end<text.length() && Character.isHighSurrogate(text.charAt(end-1))) end--;
            parts.add(text.substring(start,end));
            start=end;
        }
        return parts;
    }
}
