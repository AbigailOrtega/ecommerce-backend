package com.ecommerce.service;

import com.ecommerce.dto.request.TicketRequest;
import com.ecommerce.dto.request.TicketUpdateRequest;
import com.ecommerce.dto.response.TicketResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @Transactional
    public TicketResponse createTicket(String email, String orderNumber, TicketRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("This order does not belong to you.");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("You can only report a problem on delivered orders.");
        }
        if (ticketRepository.existsByOrderIdAndUserId(order.getId(), user.getId())) {
            throw new BadRequestException("You have already submitted a ticket for this order.");
        }

        Ticket ticket = Ticket.builder()
                .order(order)
                .user(user)
                .subject(request.subject())
                .description(request.description())
                .build();

        return mapToResponse(ticketRepository.save(ticket));
    }

    public List<TicketResponse> getUserTickets(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::mapToResponse).toList();
    }

    public TicketResponse getTicketById(String email, Long id) {
        Ticket ticket = findById(id);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        boolean isOwner = ticket.getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;
        if (!isOwner && !isAdmin) throw new AccessDeniedException("Access denied.");
        return mapToResponse(ticket);
    }

    public List<TicketResponse> getAllTickets() {
        return ticketRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public TicketResponse updateTicket(Long id, TicketUpdateRequest request) {
        Ticket ticket = findById(id);
        ticket.setStatus(request.status());
        if (request.adminNotes() != null) {
            ticket.setAdminNotes(request.adminNotes());
        }
        return mapToResponse(ticketRepository.save(ticket));
    }

    private Ticket findById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", "id", id));
    }

    private TicketResponse mapToResponse(Ticket t) {
        return new TicketResponse(
                t.getId(),
                t.getOrder().getId(),
                t.getOrder().getOrderNumber(),
                t.getUser().getId(),
                t.getUser().getFirstName() + " " + t.getUser().getLastName(),
                t.getSubject(),
                t.getDescription(),
                t.getStatus(),
                t.getAdminNotes(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
