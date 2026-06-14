package com.taskforge.ui.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectModel {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    private Integer maxMembers;
    private UserModel owner;
    private List<UserModel> members;
    private int taskCount;
    private int completedTaskCount;
    private int overdueTaskCount;
    private boolean hasCover;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getMaxMembers() { return maxMembers != null ? maxMembers : 4; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }

    public UserModel getOwner() { return owner; }
    public void setOwner(UserModel owner) { this.owner = owner; }

    public List<UserModel> getMembers() { return members; }
    public void setMembers(List<UserModel> members) { this.members = members; }

    public int getTaskCount() { return taskCount; }
    public void setTaskCount(int taskCount) { this.taskCount = taskCount; }

    public int getCompletedTaskCount() { return completedTaskCount; }
    public void setCompletedTaskCount(int completedTaskCount) { this.completedTaskCount = completedTaskCount; }

    public int getOverdueTaskCount() { return overdueTaskCount; }
    public void setOverdueTaskCount(int overdueTaskCount) { this.overdueTaskCount = overdueTaskCount; }

    public boolean isHasCover() { return hasCover; }
    public void setHasCover(boolean hasCover) { this.hasCover = hasCover; }

    public int getMemberCount() {
        int count = members != null ? members.size() : 0;
        return count + 1; // +1 for owner
    }
}
