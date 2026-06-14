package com.taskforge.service;

import com.taskforge.dto.response.ScoreResponse;
import com.taskforge.model.ContributionScore;
import com.taskforge.report.ContributionReport;
import com.taskforge.report.ProjectSummaryReport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ProjectSummaryReport projectSummaryReport;
    private final ContributionReport contributionReport;
    private final ContributionService contributionService;

    // Polymorphism: both are called as Reportable — same interface, different implementations
    @Transactional(readOnly = true)
    public Map<String, Object> generateProjectReport(Long projectId) {
        return projectSummaryReport.generate(projectId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generateContributionReport(Long projectId) {
        return contributionReport.generate(projectId);
    }

    @Transactional(readOnly = true)
    public List<ScoreResponse> getScores(Long projectId) {
        List<ContributionScore> scores = contributionService.getScores(projectId);
        return ScoreResponse.fromList(scores);
    }
}
