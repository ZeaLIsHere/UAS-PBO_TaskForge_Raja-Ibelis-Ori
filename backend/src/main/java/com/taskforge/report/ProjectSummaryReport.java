package com.taskforge.report;

import com.taskforge.dto.response.FileResponse;
import com.taskforge.dto.response.ScoreResponse;
import com.taskforge.dto.response.TaskResponse;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.model.Project;
import com.taskforge.model.task.BaseTask.TaskStatus;
import com.taskforge.repository.ContributionScoreRepository;
import com.taskforge.repository.ProjectFileRepository;
import com.taskforge.repository.ProjectRepository;
import com.taskforge.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProjectSummaryReport extends ReportGenerator<Project> {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ProjectFileRepository fileRepository;
    private final ContributionScoreRepository scoreRepository;

    @Override
    @Transactional(readOnly = true)
    protected Project collectData(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    @Override
    protected Map<String, Object> formatReport(Project project) {
        var tasks = taskRepository.findByProjectId(project.getId());

        Map<String, List<TaskResponse>> byStatus = tasks.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getStatus().name(),
                        Collectors.mapping(TaskResponse::from, Collectors.toList())));

        var scores = scoreRepository.findByProjectIdOrderByScoreDesc(project.getId());

        // Collect files across all tasks
        var allFiles = tasks.stream()
                .flatMap(t -> fileRepository.findByTaskIdOrderByUploadedAtDesc(t.getId()).stream())
                .map(FileResponse::from)
                .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportType", "PROJECT_SUMMARY");
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("projectId", project.getId());
        report.put("projectTitle", project.getTitle());
        report.put("projectDescription", project.getDescription());
        report.put("projectDeadline", project.getDeadline() != null ? project.getDeadline().toString() : null);
        report.put("ownerName", project.getOwner().getName());
        report.put("summary", Map.of(
                "totalMembers", project.getMembers().size() + 1,
                "totalTasks", tasks.size(),
                "completedTasks", tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count(),
                "overdueTasks", tasks.stream().filter(t -> t.isOverdue()).count(),
                "inProgressTasks", tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count()
        ));
        report.put("tasksByStatus", byStatus);
        report.put("contributionScores", ScoreResponse.fromList(scores));
        report.put("files", allFiles);
        return report;
    }
}
