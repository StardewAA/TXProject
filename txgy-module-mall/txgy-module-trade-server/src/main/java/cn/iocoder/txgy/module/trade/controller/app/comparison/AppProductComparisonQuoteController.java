package cn.iocoder.txgy.module.trade.controller.app.comparison;

import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.trade.controller.app.comparison.vo.AppProductComparisonQuoteReqVO;
import cn.iocoder.txgy.module.trade.controller.app.comparison.vo.AppProductComparisonQuoteRespVO;
import cn.iocoder.txgy.module.trade.service.comparison.TradeProductComparisonQuoteService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;

@RestController
@RequestMapping("/trade/product-comparison")
public class AppProductComparisonQuoteController {
    private final TradeProductComparisonQuoteService quotes;
    public AppProductComparisonQuoteController(TradeProductComparisonQuoteService quotes) { this.quotes = quotes; }

    @PostMapping("/quote")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<AppProductComparisonQuoteRespVO> quote(@Valid @RequestBody AppProductComparisonQuoteReqVO request,
                                                              HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Pragma", "no-cache");
        var member = getLoginUser();
        if (member == null || !UserTypeEnum.MEMBER.getValue().equals(member.getUserType())) {
            throw new ServiceException(403, "请使用商城会员账号");
        }
        return success(quotes.quote(member.getId(), request));
    }
}
