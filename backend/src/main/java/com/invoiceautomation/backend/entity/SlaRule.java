package com.invoiceautomation.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(
        name = "sla_rules",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_sla_rules_start_milestone_end_milestone",
                    columnNames = {"startMilestone", "endMilestone"})
        })
public class SlaRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MilestoneType startMilestone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MilestoneType endMilestone;

    @Column(nullable = false)
    private Integer targetDays;

    @Column(nullable = false)
    private Integer warningDays;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MilestoneType getStartMilestone() {
        return startMilestone;
    }

    public void setStartMilestone(MilestoneType startMilestone) {
        this.startMilestone = startMilestone;
    }

    public MilestoneType getEndMilestone() {
        return endMilestone;
    }

    public void setEndMilestone(MilestoneType endMilestone) {
        this.endMilestone = endMilestone;
    }

    public Integer getTargetDays() {
        return targetDays;
    }

    public void setTargetDays(Integer targetDays) {
        this.targetDays = targetDays;
    }

    public Integer getWarningDays() {
        return warningDays;
    }

    public void setWarningDays(Integer warningDays) {
        this.warningDays = warningDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
