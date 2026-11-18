package br.com.resenha.Resenha.model.participation;

/** Used only where the caller is allowed to see participant identity. No email/CPF/auth data. */
public record DataParticipant(Long participationId, String name) {}
