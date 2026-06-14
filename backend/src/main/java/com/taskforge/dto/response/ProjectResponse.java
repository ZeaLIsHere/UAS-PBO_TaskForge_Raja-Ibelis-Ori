package com.taskforge.dto.response;

import com.taskforge.model.Project;
import com.taskforge.model.task.BaseTask.TaskStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProjectResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private Integer maxMembers;
    private UserResponse owner;
    private List<UserResponse> members;
    private int taskCount;
    private int completedTaskCount;
    private int overdueTaskCount;
    private boolean hasCover;

    public static ProjectResponse from(Project project) {
        long total = project.getTasks().size();
        long completed = project.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long overdue = project.getTasks().stream()
                .filter(t -> t.isOverdue()).count();

        List<UserResponse> memberList = project.getMembers().stream()
                .map(UserResponse::from)
                .toList();

        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .deadline(project.getDeadline())
                .createdAt(project.getCreatedAt())
                .maxMembers(project.getMaxMembers() != null ? project.getMaxMembers() : 4)
                .owner(UserResponse.from(project.getOwner()))
                .members(memberList)
                .taskCount((int) total)
                .completedTaskCount((int) completed)
                .overdueTaskCount((int) overdue)
                .hasCover(project.getCoverPath() != null && !project.getCoverPath().isBlank())
                .build();
    }
}
