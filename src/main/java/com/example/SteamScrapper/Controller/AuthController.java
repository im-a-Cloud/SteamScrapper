package com.example.SteamScrapper.Controller;

import com.example.SteamScrapper.Model.DTO.Auth.LoginRequestDTO;
import com.example.SteamScrapper.Model.DTO.Auth.RegisterRequestDTO;
import com.example.SteamScrapper.Model.DTO.Auth.TokenResponseDTO;
import com.example.SteamScrapper.Model.DTO.Response.UsuarioResponseDTO;
import com.example.SteamScrapper.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/cadastrar")
    public ResponseEntity<TokenResponseDTO> registrar(@Valid @RequestBody RegisterRequestDTO dto) {
        return ResponseEntity.ok(authService.registrar(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        return ResponseEntity.ok(authService.login(dto));
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<UsuarioResponseDTO>> listarUsuarios() {
        return ResponseEntity.ok(authService.listarUsuarios());
    }
}