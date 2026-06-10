package br.com.resenha.Resenha.infra.security;

import lombok.Builder;

@Builder
public record JWTUserData(Long userId, String email) {
}