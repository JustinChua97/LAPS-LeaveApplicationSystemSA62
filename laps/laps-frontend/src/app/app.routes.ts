import { Routes } from '@angular/router';
import { inject } from '@angular/core';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { AuthService } from './services/auth.service';

// Route guard — safety net if the JWT somehow disappears (e.g. sessionStorage cleared).
// After APP_INITIALIZER, the token should always be present.
const authGuard = () => {
  if (inject(AuthService).isLoggedIn()) return true;
  window.location.href = '/login';   // Spring Boot's login page, not an Angular route
  return false;
};

export const routes: Routes = [
  { path: '',          redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: '**',        redirectTo: 'dashboard' },
];
