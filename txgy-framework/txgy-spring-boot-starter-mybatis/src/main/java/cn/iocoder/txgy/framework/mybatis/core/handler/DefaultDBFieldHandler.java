package cn.iocoder.txgy.framework.mybatis.core.handler;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 通用参数填充实现类
 *
 * 如果没有显式的对通用参数进行赋值，这里会对通用参数进行填充、赋值
 *
 * @author hexiaowu
 */
public class DefaultDBFieldHandler implements MetaObjectHandler {

    /**
     * 系统操作者标识：当写入动作没有登录上下文时（例如 WMS / MES 等外部系统通过 RPC
     * 机器回调写入 ERP 数据），使用该值填充 creator / updater。
     *
     * 部分表的 creator / updater 为 NOT NULL，若不回退会直接抛
     * SQLIntegrityConstraintViolationException，使机器回调整体失败。
     */
    public static final String SYSTEM_OPERATOR = "0";

    @Override
    @SuppressWarnings("PatternVariableCanBeUsed")
    public void insertFill(MetaObject metaObject) {
        if (Objects.nonNull(metaObject) && metaObject.getOriginalObject() instanceof BaseDO) {
            BaseDO baseDO = (BaseDO) metaObject.getOriginalObject();

            LocalDateTime current = LocalDateTime.now();
            // 创建时间为空，则以当前时间为插入时间
            if (Objects.isNull(baseDO.getCreateTime())) {
                baseDO.setCreateTime(current);
            }
            // 更新时间为空，则以当前时间为更新时间
            if (Objects.isNull(baseDO.getUpdateTime())) {
                baseDO.setUpdateTime(current);
            }

            Long userId = SecurityFrameworkUtils.getLoginUserId();
            // 有登录上下文时以登录用户作为操作者，没有登录上下文（机器回调）时回退为系统操作者
            String operator = Objects.nonNull(userId) ? userId.toString() : SYSTEM_OPERATOR;
            // 创建人为空，则填充操作者
            if (Objects.isNull(baseDO.getCreator())) {
                baseDO.setCreator(operator);
            }
            // 更新人为空，则填充操作者
            if (Objects.isNull(baseDO.getUpdater())) {
                baseDO.setUpdater(operator);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时间为空，则以当前时间为更新时间
        Object modifyTime = getFieldValByName("updateTime", metaObject);
        if (Objects.isNull(modifyTime)) {
            setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }

        // 更新人为空，则填充操作者（无登录上下文时回退为系统操作者）
        Object modifier = getFieldValByName("updater", metaObject);
        if (Objects.isNull(modifier)) {
            Long userId = SecurityFrameworkUtils.getLoginUserId();
            setFieldValByName("updater",
                    Objects.nonNull(userId) ? userId.toString() : SYSTEM_OPERATOR, metaObject);
        }
    }
}
