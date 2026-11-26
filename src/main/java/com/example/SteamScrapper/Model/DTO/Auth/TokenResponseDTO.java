package com.example.SteamScrapper.Model.DTO.Auth;

public record TokenResponseDTO(
        String token,
        Long usuarioId,
        String email,
        String nome
) {
}