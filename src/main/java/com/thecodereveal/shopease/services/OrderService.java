package com.thecodereveal.shopease.services;

import com.thecodereveal.shopease.auth.entities.User;
import com.thecodereveal.shopease.dto.OrderDetails;
import com.thecodereveal.shopease.dto.OrderItemDetail;
import com.thecodereveal.shopease.dto.OrderRequest;
import com.thecodereveal.shopease.entities.*;
import com.thecodereveal.shopease.repositories.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductService productService;

    @Transactional
    public Order createOrder(OrderRequest orderRequest, Principal principal) throws Exception {
        User user = (User) userDetailsService.loadUserByUsername(principal.getName());

        Address address = user.getAddressList().stream()
                .filter(a -> orderRequest.getAddressId().equals(a.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid address ID"));

        Order order = Order.builder()
                .user(user)
                .address(address)
                .totalAmount(orderRequest.getTotalAmount())
                .orderDate(orderRequest.getOrderDate() != null ? orderRequest.getOrderDate() : new Date())
                .discount(orderRequest.getDiscount())
                .expectedDeliveryDate(orderRequest.getExpectedDeliveryDate())
                .paymentMethod(orderRequest.getPaymentMethod())
                .orderStatus(OrderStatus.PENDING)
                .build();

        List<OrderItem> orderItems = orderRequest.getOrderItemRequests().stream().map(item -> {
            Product product = null;
            try {
                product = productService.fetchProductById(item.getProductId());
            } catch (Exception e) {
                throw new RuntimeException("Product not found with ID: " + item.getProductId());
            }

            return OrderItem.builder()
                    .product(product)
                    .productVariantId(item.getProductVariantId())
                    .quantity(item.getQuantity())
                    .order(order)
                    .build();
        }).collect(Collectors.toList());

        order.setOrderItemList(orderItems);

        Payment payment = Payment.builder()
                .paymentStatus(PaymentStatus.PENDING)
                .paymentDate(new Date())
                .order(order)
                .amount(order.getTotalAmount())
                .paymentMethod(order.getPaymentMethod())
                .build();

        order.setPayment(payment);

        return orderRepository.save(order);
    }

    public List<OrderDetails> getOrdersByUser(String username) {
        User user = (User) userDetailsService.loadUserByUsername(username);
        List<Order> orders = orderRepository.findByUser(user);

        return orders.stream().map(order -> OrderDetails.builder()
                .id(order.getId())
                .orderDate(order.getOrderDate())
                .orderStatus(order.getOrderStatus())
                .shipmentNumber(order.getShipmentTrackingNumber())
                .address(order.getAddress())
                .totalAmount(order.getTotalAmount())
                .orderItemList(getItemDetails(order.getOrderItemList()))
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .build()
        ).collect(Collectors.toList());
    }

    private List<OrderItemDetail> getItemDetails(List<OrderItem> orderItemList) {
        return orderItemList.stream().map(orderItem -> OrderItemDetail.builder()
                .id(orderItem.getId())
                .itemPrice(orderItem.getItemPrice())
                .product(orderItem.getProduct())
                .productVariantId(orderItem.getProductVariantId())
                .quantity(orderItem.getQuantity())
                .build()
        ).collect(Collectors.toList());
    }

    public void cancelOrder(UUID id, Principal principal) {
        User user = (User) userDetailsService.loadUserByUsername(principal.getName());
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + id));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized to cancel this order");
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
}
