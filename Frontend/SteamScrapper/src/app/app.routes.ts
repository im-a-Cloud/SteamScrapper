import { Routes } from '@angular/router';
import { Login } from './pages/login/login';
import { Perfil } from './pages/perfil/perfil';
import { Cadastro } from './pages/cadastro/cadastro';

export const routes: Routes = [
    {path: 'cadastro', component: Cadastro},
    {path: 'login', component: Login },
];
