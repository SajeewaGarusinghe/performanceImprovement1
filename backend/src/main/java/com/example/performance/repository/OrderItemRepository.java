package com.example.performance.repository;

import com.example.performance.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByCustomerId(Long customerId);

    @Query("select oi from OrderItem oi where oi.customer.id in :ids")
    List<OrderItem> findByCustomerIds(@Param("ids") List<Long> customerIds);
}


