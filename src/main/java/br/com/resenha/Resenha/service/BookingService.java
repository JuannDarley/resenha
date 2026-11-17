package br.com.resenha.Resenha.service;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.location.court.CourtRepository;
import br.com.resenha.Resenha.model.user.UserRepository;
import br.com.resenha.Resenha.validation.booking.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import java.time.*;
import java.util.List;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final CourtRepository courts;
    private final UserRepository users;
    private final List<BookingValidator> validators;
    private final Clock clock;

    public BookingService(BookingRepository bookings, CourtRepository courts, UserRepository users,
            List<BookingValidator> validators, Clock clock) {
        this.bookings = bookings; this.courts = courts; this.users = users;
        this.validators = validators; this.clock = clock;
    }

    @Transactional
    public DataBooking create(Long creatorId, DataCreateBooking data) {
        // Always lock creator before court: protects the daily quota across different courts too.
        var creator = users.findByIdForUpdate(creatorId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        var court = courts.findByIdForUpdate(data.courtId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Quadra não encontrada"));
        // Evaluate time after obtaining the locks, in case the request had to wait.
        var now = LocalDateTime.now(clock);
        var context = new BookingContext(creatorId, court, data, now);
        validators.forEach(validator -> validator.validate(context));
        try {
            return new DataBooking(bookings.saveAndFlush(new Booking(court, creator, data, now)));
        } catch (DataIntegrityViolationException conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Horário já reservado ou conflitante", conflict);
        }
    }

    @Transactional(readOnly = true)
    public Page<DataBooking> list(Long creatorId, Pageable pageable) {
        return bookings.findAllByCreatorId(creatorId, pageable).map(DataBooking::new);
    }

    @Transactional(readOnly = true)
    public DataBooking detail(Long id, Long creatorId) {
        return bookings.findByIdAndCreatorId(id, creatorId).map(DataBooking::new).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Agendamento não encontrado"));
    }
}
