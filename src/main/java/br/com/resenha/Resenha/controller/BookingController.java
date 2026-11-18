package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.user.User;
import br.com.resenha.Resenha.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/bookings")
@PreAuthorize("hasRole('PLAYER')")
public class BookingController {
    private final BookingService bookings;
    public BookingController(BookingService bookings) { this.bookings = bookings; }

    @GetMapping("/simulated-price")
    public java.math.BigDecimal simulatedPrice() {
        return Booking.SIMULATED_PRICE;
    }

    @PostMapping
    public ResponseEntity<DataBooking> create(@Valid @RequestBody DataCreateBooking data,
            @AuthenticationPrincipal User creator, UriComponentsBuilder uri) {
        DataBooking result = bookings.create(creator.getId(), data);
        return ResponseEntity.created(uri.path("/bookings/{id}").buildAndExpand(result.id()).toUri()).body(result);
    }

    @GetMapping
    public Page<DataBooking> mine(@AuthenticationPrincipal User creator,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookings.list(creator.getId(), pageable);
    }

    @GetMapping("/{id}")
    public DataBooking detail(@PathVariable Long id, @AuthenticationPrincipal User creator) {
        return bookings.detail(id, creator.getId());
    }
}
