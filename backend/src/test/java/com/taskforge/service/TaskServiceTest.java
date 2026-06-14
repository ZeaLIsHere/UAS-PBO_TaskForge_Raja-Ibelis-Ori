package com.taskforge.service;

import com.taskforge.dto.request.TaskStatusRequest;
import com.taskforge.dto.response.TaskResponse;
import com.taskforge.model.Project;
import com.taskforge.model.User;
import com.taskforge.model.User.Role;
import com.taskforge.model.task.BaseTask.Priority;
import com.taskforge.model.task.BaseTask.TaskStatus;
import com.taskforge.model.task.SimpleTask;
import com.taskforge.repository.TaskRepository;
import com.taskforge.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectService projectService;
    @Mock private ActivityLogService activityLogService;
    @Mock private ContributionService contributionService;
    @InjectMocks private TaskService taskService;

    private User ketua;
    private User anggota;
    private Project project;
    private SimpleTask task;

    @BeforeEach
    void setUp() {
        ketua = User.builder().id(1L).name("Ketua").email("ketua@test.com").role(Role.KETUA).build();
        anggota = User.builder().id(2L).name("Budi").email("budi@test.com").role(Role.ANGGOTA).build();

        project = Project.builder().id(1L).title("Proyek").owner(ketua)
                .members(new HashSet<>()).tasks(new ArrayList<>())
                .activityLogs(new ArrayList<>()).contributionScores(new ArrayList<>()).build();
        project.getMembers().add(anggota);

        task = new SimpleTask();
        task.setId(5L);
        task.setTitle("Task Test");
        task.setPriority(Priority.HIGH);
        task.setDeadline(LocalDateTime.now().plusDays(3));
        task.setProject(project);
        task.reassign(anggota);
    }

    @Test
    void isAssignee_returnsTrueForActualAssignee() {
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        assertThat(taskService.isAssignee(5L, "budi@test.com")).isTrue();
    }

    @Test
    void isAssignee_returnsFalseForOtherUser() {
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        assertThat(taskService.isAssignee(5L, "ketua@test.com")).isFalse();
    }

    @Test
    void updateStatus_toDone_setsCompletedAt() {
        TaskStatusRequest req = new TaskStatusRequest();
        req.setStatus(TaskStatus.DONE);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectService.getUser("ketua@test.com")).thenReturn(ketua);
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse result = taskService.updateStatus(5L, "ketua@test.com", req);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void simpleTask_scoreCalculation_highPriorityOnTime() {
        // HIGH = 3 weight, completed before deadline → +1 bonus → score = 4
        task.complete();  // completedAt set to now, deadline is 3 days from now → on time
        assertThat(task.getScore()).isEqualTo(4.0); // HIGH(3) + ontime bonus(1)
    }

    @Test
    void simpleTask_scoreCalculation_mediumPriorityLate() {
        task.setPriority(Priority.MEDIUM);
        task.setDeadline(LocalDateTime.now().minusDays(1)); // already past
        task.complete();
        // completedAt = now, deadline = yesterday → NOT on time → score = MEDIUM(2) + 0
        assertThat(task.getScore()).isEqualTo(2.0);
    }

    @Test
    void updateStatus_toInProgress_doesNotSetCompletedAt() {
        TaskStatusRequest req = new TaskStatusRequest();
        req.setStatus(TaskStatus.IN_PROGRESS);

        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));
        when(projectService.getUser("budi@test.com")).thenReturn(anggota);
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse result = taskService.updateStatus(5L, "budi@test.com", req);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(result.getCompletedAt()).isNull();
    }
}
