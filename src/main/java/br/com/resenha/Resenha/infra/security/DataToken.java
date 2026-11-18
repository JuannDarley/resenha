package br.com.resenha.Resenha.infra.security;

import br.com.resenha.Resenha.model.user.Role;

public record DataToken(String token, Role role) {
}
