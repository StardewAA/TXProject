package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Decision;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Issue;
import cn.iocoder.txgy.module.cms.service.review.ReviewModels.Metadata;

import java.util.List;

public interface CmsComplianceReviewer {
    Assessment review(Metadata metadata, String text, List<Issue> deterministicIssues);
    record Assessment(Decision decision, int score, List<Issue> issues, String model) { }
}
