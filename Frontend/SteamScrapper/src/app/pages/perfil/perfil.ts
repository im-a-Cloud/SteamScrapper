import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './perfil.html',
  styleUrl: './perfil.css',
})
export class Perfil implements OnInit {
  private router = inject(Router);

  ngOnInit(): void {
    // check auth, load user data, etc.
  }

  logout(): void {
    localStorage.removeItem('token');
    this.router.navigate(['/']);
  }
}