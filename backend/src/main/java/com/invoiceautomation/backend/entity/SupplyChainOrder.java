package com.invoiceautomation.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(
        name = "orders",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_orders_order_number", columnNames = "orderNumber")
        })
public class SupplyChainOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String orderNumber;

    @Column(nullable = false, length = 255)
    private String customerName;

    @Column(nullable = false, length = 255)
    private String supplierName;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, length = 128)
    private String originCountry;

    @Column(nullable = false, length = 128)
    private String destinationCountry;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal orderValue;

    @Column(length = 1024)
    private String notes;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt asc")
    private List<OrderMilestone> milestones = new ArrayList<>();

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

    public void addMilestone(OrderMilestone milestone) {
        milestones.add(milestone);
        milestone.setOrder(this);
    }

    public void removeMilestone(OrderMilestone milestone) {
        milestones.remove(milestone);
        milestone.setOrder(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getOriginCountry() {
        return originCountry;
    }

    public void setOriginCountry(String originCountry) {
        this.originCountry = originCountry;
    }

    public String getDestinationCountry() {
        return destinationCountry;
    }

    public void setDestinationCountry(String destinationCountry) {
        this.destinationCountry = destinationCountry;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getOrderValue() {
        return orderValue;
    }

    public void setOrderValue(BigDecimal orderValue) {
        this.orderValue = orderValue;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<OrderMilestone> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<OrderMilestone> milestones) {
        this.milestones = milestones;
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
