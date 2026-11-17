package br.com.resenha.Resenha.model.location.availability;

import java.time.LocalDateTime;

public record DataSlot(LocalDateTime startsAt, LocalDateTime endsAt, boolean available, SlotStatus status) {
    public DataSlot(LocalDateTime startsAt, LocalDateTime endsAt, boolean available) {
        this(startsAt, endsAt, available, available ? SlotStatus.AVAILABLE : SlotStatus.BLOCKED);
    }
}
