package com.werealestate.backend.dto;

public record LoginResponse(String token, UsuarioDto user) {
}
