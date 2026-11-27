package com.example.SteamScrapper.Model.DTO.Response;

import com.example.SteamScrapper.Model.Entities.Usuario;

public record UsuarioResponseDTO(
        Long id,
        String nome,
        String email,
        String linkPerfilSteam
) {
    public static UsuarioResponseDTO from(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNomeUsuario(),
                usuario.getEmailUsuario(),
                usuario.getLinkPerfilSteam()
        );
    }
}