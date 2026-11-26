package com.example.SteamScrapper.Model.Repository;

import com.example.SteamScrapper.Model.Entities.Usuario;

import java.util.Optional;

public interface UsuarioRepository extends SoftDeletableRepository<Usuario> {

    Optional<Usuario> findByEmailUsuario(String emailUsuario);

    boolean existsByEmailUsuario(String emailUsuario);
}
