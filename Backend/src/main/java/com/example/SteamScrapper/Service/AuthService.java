package com.example.SteamScrapper.Service;

import com.example.SteamScrapper.Model.DTO.Auth.LoginRequestDTO;
import com.example.SteamScrapper.Model.DTO.Auth.RegisterRequestDTO;
import com.example.SteamScrapper.Model.DTO.Auth.TokenResponseDTO;
import com.example.SteamScrapper.Model.DTO.Response.UsuarioResponseDTO;
import com.example.SteamScrapper.Model.Entities.Usuario;
import com.example.SteamScrapper.Model.Repository.UsuarioRepository;
import com.example.SteamScrapper.Security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public TokenResponseDTO registrar(RegisterRequestDTO dto) {
        if (usuarioRepository.existsByEmailUsuario(dto.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email já cadastrado");
        }

        Usuario usuario = new Usuario();
        usuario.setEmailUsuario(dto.email());
        usuario.setSenhaUsuario(passwordEncoder.encode(dto.senha()));
        usuario.setNomeUsuario(dto.nome());
        usuario.setLinkPerfilSteam(dto.linkSteam());
        if (usuarioRepository.existsByEmailUsuario(dto.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Email já cadastrado");
        }
        if (usuarioRepository.existsByLinkPerfilSteam(dto.linkSteam())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Link da Steam já cadastrado");
        }
        if (usuarioRepository.existsByNomeUsuario(dto.nome())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Nome de usuário já em uso");
        }

        usuario = usuarioRepository.save(usuario);

        String token = jwtService.gerarToken(usuario.getId(), usuario.getEmailUsuario());

        return new TokenResponseDTO(token, usuario.getId(), usuario.getEmailUsuario(), usuario.getNomeUsuario(), usuario.getLinkPerfilSteam());
    }

    public TokenResponseDTO login(LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByEmailUsuario(dto.email())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Email ou senha inválidos"));

        if (!passwordEncoder.matches(dto.senha(), usuario.getSenhaUsuario())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Email ou senha inválidos");
        }

        String token = jwtService.gerarToken(usuario.getId(), usuario.getEmailUsuario());

        return new TokenResponseDTO(token, usuario.getId(), usuario.getEmailUsuario(), usuario.getNomeUsuario(), usuario.getLinkPerfilSteam());
    }
    public List<UsuarioResponseDTO> listarUsuarios() {
        return usuarioRepository.findAll()
                .stream()
                .map(UsuarioResponseDTO::from)
                .toList();
    }
}
