package com.taskforge.service;

import com.taskforge.dto.request.MemberRequest;
import com.taskforge.dto.request.ProjectRequest;
import com.taskforge.dto.response.ProjectResponse;
import com.taskforge.exception.DuplicateResourceException;
import com.taskforge.exception.ResourceNotFoundException;
import com.taskforge.model.Project;
import com.taskforge.model.User;
import com.taskforge.model.User.Role;
import com.taskforge.repository.ProjectRepository;
import com.taskforge.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityLogService activityLogService;
    @InjectMocks private ProjectService projectService;

    private User ketua;
    private User anggota;
    private Project project;

    @BeforeEach
    void setUp() {
        ketua = User.builder().id(1L).name("Ketua").email("ketua@test.com").role(Role.KETUA).build();
        anggota = User.builder().id(2L).name("Budi").email("budi@test.com").role(Role.ANGGOTA).build();

        project = Project.builder()
                .id(10L).title("Proyek Test")
                .maxMembers(4)
                .owner(ketua)
                .members(new HashSet<>())
                .tasks(new ArrayList<>())
                .activityLogs(new ArrayList<>())
                .contributionScores(new ArrayList<>())
                .build();
    }

    @Test
    void createProject_success() {
        ProjectRequest req = new ProjectRequest();
        req.setTitle("Proyek Baru");
        req.setMaxMembers(4);

        when(userRepository.findByEmail("ketua@test.com")).thenReturn(Optional.of(ketua));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> {
            Project p = inv.getArgument(0);
            p = Project.builder().id(1L).title(p.getTitle()).owner(ketua)
                    .members(new HashSet<>()).tasks(new ArrayList<>())
                    .activityLogs(new ArrayList<>()).contributionScores(new ArrayList<>()).build();
            return p;
        });

        ProjectResponse result = projectService.createProject("ketua@test.com", req);

        assertThat(result.getTitle()).isEqualTo("Proyek Baru");
        assertThat(result.getOwner().getEmail()).isEqualTo("ketua@test.com");
    }

    @Test
    void isMember_ownerIsAlwaysMember() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        assertThat(projectService.isMember(10L, "ketua@test.com")).isTrue();
    }

    @Test
    void isMember_addedMemberReturnsTrue() {
        project.getMembers().add(anggota);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        assertThat(projectService.isMember(10L, "budi@test.com")).isTrue();
    }

    @Test
    void isMember_strangerReturnsFalse() {
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        assertThat(projectService.isMember(10L, "stranger@test.com")).isFalse();
    }

    @Test
    void addMember_duplicateThrowsException() {
        project.getMembers().add(anggota);
        MemberRequest req = new MemberRequest();
        req.setEmail("budi@test.com");

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findByEmail("budi@test.com")).thenReturn(Optional.of(anggota));

        assertThatThrownBy(() -> projectService.addMember(10L, "ketua@test.com", req))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void addMember_unknownEmailThrowsException() {
        MemberRequest req = new MemberRequest();
        req.setEmail("unknown@test.com");

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.addMember(10L, "ketua@test.com", req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMyProjects_returnsProjectsForUser() {
        when(userRepository.findByEmail("ketua@test.com")).thenReturn(Optional.of(ketua));
        when(projectRepository.findAllByMemberOrOwner(ketua)).thenReturn(List.of(project));

        List<ProjectResponse> result = projectService.getMyProjects("ketua@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Proyek Test");
    }
}
