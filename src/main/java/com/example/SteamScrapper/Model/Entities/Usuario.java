package com.example.SteamScrapper.Model.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name= "tb_usuario")
@ToString
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Usuario extends AbstractModel {
    @NotBlank
    @Column(nullable = false, unique = true)
    private String nomeUsuario;
    @NotBlank
    @Column(nullable = false, unique = true)
    private String emailUsuario;
    @NotBlank
    @Column(nullable = false, unique = true)
    private String senhaUsuario;

    //links de WL na steam seguem esse formato
    //https://store.steampowered.com/wishlist/profiles/76561198120867657/?sort=discount
    //exemplo de link de perfilstea
    //https://steamcommunity.com/profiles/76561198120867657/
    @NotBlank
    @Column(nullable = false, unique = true)
    private String linkPerfilSteam;
}
