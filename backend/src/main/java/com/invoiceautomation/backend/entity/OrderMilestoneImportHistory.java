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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "order_milestone_import_history")
public class OrderMilestoneImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private SupplyChainOrder order;

    @Enumerated(EnumType.STRING)
    @Column(name = "milestone_type", nullable = false, length = 64)
    private MilestoneType milestoneType;

    @Column(name = "previous_occurred_at")
    private LocalDateTime previousOccurredAt;

    @Column(name = "new_occurred_at", nullable = false)
    private LocalDateTime newOccurredAt;

    @Column(name = "previous_notes", length = 1024)
    private String previousNotes;

    @Column(name = "new_notes", length = 1024)
    private String newNotes;

    @Column(name = "source_file_name", nullable = false, length = 255)
    private String sourceFileName;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @PrePersist
    public void onCreate() {
        importedAt = LocalDateTime.now();
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

    public LocalDateTime getPreviousOccurredAt() {
        return previousOccurredAt;
    }

    public void setPreviousOccurredAt(LocalDateTime previousOccurredAt) {
        this.previousOccurredAt = previousOccurredAt;
    }

    public LocalDateTime getNewOccurredAt() {
        return newOccurredAt;
    }

    public void setNewOccurredAt(LocalDateTime newOccurredAt) {
        this.newOccurredAt = newOccurredAt;
    }

    public String getPreviousNotes() {
        return previousNotes;
    }

    public void setPreviousNotes(String previousNotes) {
        this.previousNotes = previousNotes;
    }

    public String getNewNotes() {
        return newNotes;
    }

    public void setNewNotes(String newNotes) {
        this.newNotes = newNotes;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }
}
