package com.ecommerce.service;

import com.ecommerce.dto.request.TicketRequest;
import com.ecommerce.dto.request.TicketUpdateRequest;
import com.ecommerce.dto.response.TicketResponse;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BadRequestException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService")
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private TicketService ticketService;

    private User customer;
    private User admin;
    private Order deliveredOrder;
    private Ticket openTicket;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(10L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .password("hashed")
                .role(Role.CUSTOMER)
                .build();

        admin = User.builder()
                .id(99L)
                .firstName("Admin")
                .lastName("User")
                .email("admin@example.com")
                .password("hashed")
                .role(Role.ADMIN)
                .build();

        deliveredOrder = Order.builder()
                .id(50L)
                .orderNumber("ORD-ABC123")
                .user(customer)
                .totalAmount(BigDecimal.valueOf(59.99))
                .status(OrderStatus.DELIVERED)
                .shippingAddress("123 Main St")
                .shippingCity("Monterrey")
                .shippingState("NL")
                .shippingZipCode("64000")
                .shippingCountry("Mexico")
                .items(new ArrayList<>())
                .build();

        openTicket = Ticket.builder()
                .id(300L)
                .order(deliveredOrder)
                .user(customer)
                .subject("Missing item")
                .description("One item was not in the package.")
                .status(TicketStatus.OPEN)
                .adminNotes(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─── createTicket ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createTicket")
    class CreateTicket {

        private TicketRequest validRequest() {
            return new TicketRequest("Missing item", "One item was not in the package.");
        }

        @Test
        @DisplayName("creates and returns ticket for a valid delivered order")
        void createTicket_success() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(orderRepository.findByOrderNumber("ORD-ABC123")).thenReturn(Optional.of(deliveredOrder));
            when(ticketRepository.existsByOrderIdAndUserId(50L, 10L)).thenReturn(false);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(300L);
                return t;
            });

            TicketResponse result = ticketService.createTicket("jane@example.com", "ORD-ABC123", validRequest());

            assertThat(result.id()).isEqualTo(300L);
            assertThat(result.subject()).isEqualTo("Missing item");
            assertThat(result.description()).isEqualTo("One item was not in the package.");
            assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user email does not exist")
        void createTicket_userNotFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.createTicket("ghost@example.com", "ORD-ABC123", validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("email");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order number does not exist")
        void createTicket_orderNotFound() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(orderRepository.findByOrderNumber("ORD-GHOST")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.createTicket("jane@example.com", "ORD-GHOST", validRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("orderNumber");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws AccessDeniedException when order belongs to a different user")
        void createTicket_orderDoesNotBelongToUser() {
            User otherUser = User.builder()
                    .id(77L).firstName("Other").lastName("Person")
                    .email("other@example.com").password("pw").role(Role.CUSTOMER)
                    .build();
            // The order belongs to customer (id=10), not otherUser (id=77)
            when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));
            when(orderRepository.findByOrderNumber("ORD-ABC123")).thenReturn(Optional.of(deliveredOrder));

            assertThatThrownBy(() -> ticketService.createTicket("other@example.com", "ORD-ABC123", validRequest()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("belong");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BadRequestException when order status is not DELIVERED")
        void createTicket_orderNotDelivered() {
            deliveredOrder.setStatus(OrderStatus.SHIPPED);
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(orderRepository.findByOrderNumber("ORD-ABC123")).thenReturn(Optional.of(deliveredOrder));

            assertThatThrownBy(() -> ticketService.createTicket("jane@example.com", "ORD-ABC123", validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("delivered orders");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws BadRequestException when a ticket for this order already exists")
        void createTicket_duplicateTicket() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(orderRepository.findByOrderNumber("ORD-ABC123")).thenReturn(Optional.of(deliveredOrder));
            when(ticketRepository.existsByOrderIdAndUserId(50L, 10L)).thenReturn(true);

            assertThatThrownBy(() -> ticketService.createTicket("jane@example.com", "ORD-ABC123", validRequest()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already submitted");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("ticket is saved with OPEN status by default")
        void createTicket_defaultStatusIsOpen() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(orderRepository.findByOrderNumber("ORD-ABC123")).thenReturn(Optional.of(deliveredOrder));
            when(ticketRepository.existsByOrderIdAndUserId(50L, 10L)).thenReturn(false);

            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            when(ticketRepository.save(captor.capture())).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(301L);
                return t;
            });

            ticketService.createTicket("jane@example.com", "ORD-ABC123", validRequest());

            assertThat(captor.getValue().getStatus()).isEqualTo(TicketStatus.OPEN);
        }

        @Test
        @DisplayName("response contains correct orderId and orderNumber from the saved ticket")
        void createTicket_responseContainsOrderInfo() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(orderRepository.findByOrderNumber("ORD-ABC123")).thenReturn(Optional.of(deliveredOrder));
            when(ticketRepository.existsByOrderIdAndUserId(50L, 10L)).thenReturn(false);
            when(ticketRepository.save(any())).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(302L);
                return t;
            });

            TicketResponse result = ticketService.createTicket("jane@example.com", "ORD-ABC123", validRequest());

            assertThat(result.orderId()).isEqualTo(50L);
            assertThat(result.orderNumber()).isEqualTo("ORD-ABC123");
            assertThat(result.userName()).isEqualTo("Jane Doe");
        }
    }

    // ─── getUserTickets ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserTickets")
    class GetUserTickets {

        @Test
        @DisplayName("returns list of tickets for an existing user")
        void getUserTickets_success() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(ticketRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(openTicket));

            List<TicketResponse> result = ticketService.getUserTickets("jane@example.com");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).subject()).isEqualTo("Missing item");
        }

        @Test
        @DisplayName("returns empty list when user has no tickets")
        void getUserTickets_noTickets() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(ticketRepository.findByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

            assertThat(ticketService.getUserTickets("jane@example.com")).isEmpty();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user email does not exist")
        void getUserTickets_userNotFound() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.getUserTickets("ghost@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("email");

            verify(ticketRepository, never()).findByUserIdOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("passes user id to repository query")
        void getUserTickets_passesUserIdToRepo() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(ticketRepository.findByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

            ticketService.getUserTickets("jane@example.com");

            verify(ticketRepository).findByUserIdOrderByCreatedAtDesc(10L);
        }

        @Test
        @DisplayName("maps all ticket fields to response correctly")
        void getUserTickets_mapsFields() {
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));
            when(ticketRepository.findByUserIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(openTicket));

            TicketResponse r = ticketService.getUserTickets("jane@example.com").get(0);

            assertThat(r.id()).isEqualTo(300L);
            assertThat(r.orderId()).isEqualTo(50L);
            assertThat(r.orderNumber()).isEqualTo("ORD-ABC123");
            assertThat(r.userId()).isEqualTo(10L);
            assertThat(r.userName()).isEqualTo("Jane Doe");
            assertThat(r.status()).isEqualTo(TicketStatus.OPEN);
            assertThat(r.adminNotes()).isNull();
        }
    }

    // ─── getTicketById ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTicketById")
    class GetTicketById {

        @Test
        @DisplayName("owner can view their own ticket")
        void getTicketById_ownerCanView() {
            when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
            when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(customer));

            TicketResponse result = ticketService.getTicketById("jane@example.com", 300L);

            assertThat(result.id()).isEqualTo(300L);
            assertThat(result.subject()).isEqualTo("Missing item");
        }

        @Test
        @DisplayName("admin can view any ticket")
        void getTicketById_adminCanView() {
            when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
            when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

            TicketResponse result = ticketService.getTicketById("admin@example.com", 300L);

            assertThat(result.id()).isEqualTo(300L);
        }

        @Test
        @DisplayName("throws AccessDeniedException when a different non-admin user tries to view")
        void getTicketById_otherUserDenied() {
            User otherUser = User.builder()
                    .id(77L).firstName("Other").lastName("Person")
                    .email("other@example.com").password("pw").role(Role.CUSTOMER)
                    .build();

            when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
            when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

            assertThatThrownBy(() -> ticketService.getTicketById("other@example.com", 300L))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when ticket does not exist")
        void getTicketById_ticketNotFound() {
            when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.getTicketById("jane@example.com", 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Ticket");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when user email does not exist")
        void getTicketById_userNotFound() {
            when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.getTicketById("ghost@example.com", 300L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("email");
        }
    }

    // ─── getAllTickets ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllTickets")
    class GetAllTickets {

        @Test
        @DisplayName("returns all tickets ordered by creation date")
        void getAllTickets_success() {
            when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(openTicket));

            List<TicketResponse> result = ticketService.getAllTickets();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(300L);
        }

        @Test
        @DisplayName("returns empty list when no tickets exist")
        void getAllTickets_empty() {
            when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            assertThat(ticketService.getAllTickets()).isEmpty();
        }

        @Test
        @DisplayName("maps all tickets in the list to response objects")
        void getAllTickets_mapsAll() {
            Ticket secondTicket = Ticket.builder()
                    .id(301L)
                    .order(deliveredOrder)
                    .user(admin)
                    .subject("Wrong size")
                    .description("Received wrong size.")
                    .status(TicketStatus.IN_PROGRESS)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(ticketRepository.findAllByOrderByCreatedAtDesc())
                    .thenReturn(List.of(openTicket, secondTicket));

            List<TicketResponse> result = ticketService.getAllTickets();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TicketResponse::id).containsExactly(300L, 301L);
        }

        @Test
        @DisplayName("calls findAllByOrderByCreatedAtDesc on repository")
        void getAllTickets_callsCorrectRepoMethod() {
            when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            ticketService.getAllTickets();

            verify(ticketRepository).findAllByOrderByCreatedAtDesc();
        }
    }

    // ─── updateTicket ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateTicket")
    class UpdateTicket {

        @Test
        @DisplayName("updates status and returns response")
        void updateTicket_updatesStatus() {
            TicketUpdateRequest request = new TicketUpdateRequest(TicketStatus.IN_PROGRESS, null);

            when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            TicketResponse result = ticketService.updateTicket(300L, request);

            assertThat(result.status()).isEqualTo(TicketStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("updates adminNotes when provided")
        void updateTicket_updatesAdminNotes() {
            TicketUpdateRequest request = new TicketUpdateRequest(TicketStatus.RESOLVED, "Refund issued.");

            when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

            TicketResponse result = ticketService.updateTicket(300L, request);

            assertThat(result.adminNotes()).isEqualTo("Refund issued.");
            assertThat(result.status()).isEqualTo(TicketStatus.RESOLVED);
        }

        @Test
        @DisplayName("does not overwrite existing adminNotes when request provides null")
        void updateTicket_nullAdminNotesNotOverwritten() {
            openTicket.setAdminNotes("Previous note.");
            TicketUpdateRequest request = new TicketUpdateRequest(TicketStatus.CLOSED, null);

            when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            when(ticketRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            ticketService.updateTicket(300L, request);

            assertThat(captor.getValue().getAdminNotes()).isEqualTo("Previous note.");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when ticket does not exist")
        void updateTicket_ticketNotFound() {
            TicketUpdateRequest request = new TicketUpdateRequest(TicketStatus.CLOSED, null);
            when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> ticketService.updateTicket(999L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Ticket");

            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("saves the updated ticket entity")
        void updateTicket_savesEntity() {
            TicketUpdateRequest request = new TicketUpdateRequest(TicketStatus.RESOLVED, "Done.");

            when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
            when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ticketService.updateTicket(300L, request);

            verify(ticketRepository).save(openTicket);
        }

        @Test
        @DisplayName("can transition through all valid statuses")
        void updateTicket_allStatusTransitions() {
            for (TicketStatus status : TicketStatus.values()) {
                openTicket.setStatus(TicketStatus.OPEN); // reset before each iteration
                TicketUpdateRequest request = new TicketUpdateRequest(status, null);
                when(ticketRepository.findById(300L)).thenReturn(Optional.of(openTicket));
                when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                TicketResponse result = ticketService.updateTicket(300L, request);

                assertThat(result.status()).isEqualTo(status);
            }
        }
    }
}
