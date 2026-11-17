package br.com.resenha.Resenha.validation.booking;

/** Each rule has its own implementation and unit tests. Spring orders validators using @Order. */
public interface BookingValidator {
    void validate(BookingContext context);
}
