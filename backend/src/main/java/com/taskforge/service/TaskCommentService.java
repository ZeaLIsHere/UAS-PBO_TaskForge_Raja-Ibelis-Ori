package com.taskforge.service;

import com.taskforge.dto.request.TaskCommentRequest;
import com.taskforge.dto.response.TaskCommentResponse;
import com.taskforge.exception.ValidationException;
import com.taskforge.model.Project;
import com.taskforge.model.User;
import com.taskforge.model.task.BaseTask;
import com.taskforge.model.task.TaskComment;
import com.taskforge.repository.TaskCommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskCommentService {

    private final TaskCommentRepository commentRepository;
    private final TaskService taskService;
    private final ProjectService projectService;
    private final ContributionService contributionService;

    @Transactional(readOnly = true)
    public List<TaskCommentResponse> getComments(Long taskId, String actorEmail) {
        BaseTask task = taskService.getTask(taskId);
        User actor = projectService.getUser(actorEmail);
        
        Project project = task.getProject();
        boolean isMember = project.getOwner().getId().equals(actor.getId()) || project.getMembers().contains(actor);
        if (!isMember) {
            throw new ValidationException("Anda bukan anggota proyek ini");
        }

        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(TaskCommentResponse::from)
                .toList();
    }

    @Transactional
    public TaskCommentResponse addComment(Long taskId, String actorEmail, TaskCommentRequest request) {
        BaseTask task = taskService.getTask(taskId);
        User actor = projectService.getUser(actorEmail);

        Project project = task.getProject();
        boolean isMember = project.getOwner().getId().equals(actor.getId()) || project.getMembers().contains(actor);
        if (!isMember) {
            throw new ValidationException("Anda bukan anggota proyek ini");
        }

        TaskComment comment = TaskComment.builder()
                .task(task)
                .user(actor)
                .content(request.getContent())
                .build();

        TaskComment saved = commentRepository.save(comment);

        // Add contribution score logic as requested
        contributionService.addScoreForComment(actor, project);

        log.info("Komentar ditambahkan oleh {} pada task id={}", actor.getName(), taskId);
        return TaskCommentResponse.from(saved);
    }
}
