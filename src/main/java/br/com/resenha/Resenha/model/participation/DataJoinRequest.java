package br.com.resenha.Resenha.model.participation;

import jakarta.validation.constraints.Size;

/** An invitation is optional and never bypasses creator approval. */
public record DataJoinRequest(@Size(max = 128) String invitationCode) {}
