package com.invoiceautomation.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "alerts")
public class SupplyChainAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SupplyChainOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private MilestoneType milestoneType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AlertStatus status;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 1024)
    private String message;

    @Column
    private LocalDateTime expectedAt;

    @Column(nullable = false)
    private LocalDateTime triggeredAt;

    @Column
    private LocalDateTime resolvedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (triggeredAt == null) {
            triggeredAt = now;
        }
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

    public SupplyChainOrder getOrder() {
        return order;
    }

    public void setOrder(SupplyChainOrder order) {
        this.order = order;
    }

    public MilestoneType getMilestoneType() {
        return milestoneType;
    }

    public void setMilestoneType(MilestoneType milestoneType) {
        this.milestoneType = milestoneType;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public void setAlertType(AlertType alertType) {
        this.alertType = alertType;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getExpectedAt() {
        return expectedAt;
    }

    public void setExpectedAt(LocalDateTime expectedAt) {
        this.expectedAt = expectedAt;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
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
