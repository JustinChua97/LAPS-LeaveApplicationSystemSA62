import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, EMPTY } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';

const TOKEN_KEY = 'laps_token';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private http = inject(HttpClient);

  private fullName    = '';
  private designation = '';

  fetchJwt(): Promise<void> {
    sessionStorage.removeItem(TOKEN_KEY);
    const request$ = this.http.get<{ accessToken: string }>('/auth/jwt').pipe(
      tap(res => sessionStorage.setItem(TOKEN_KEY, res.accessToken)),
      catchError(() => {
        window.location.href = '/login';
        return EMPTY;
      })
    );
    return firstValueFrom(request$ as any) as Promise<void>;
  }

  fetchCurrentUser(): Promise<void> {
    const request$ = this.http.get<{ fullName: string; designation: string }>('/api/v1/me').pipe(
      tap(user => {
        this.fullName    = user.fullName;
        this.designation = user.designation;
      }),
      catchError(() => {
        window.location.href = '/login';
        return EMPTY;
      })
    );
    return firstValueFrom(request$ as any) as Promise<void>;
  }

  clearToken(): void {
    sessionStorage.removeItem(TOKEN_KEY);
  }

  getToken(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    return this.getToken() !== null;
  }

  getFullName(): string {
    return this.fullName;
  }

  getDesignation(): string {
    return this.designation;
  }

  logout(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    window.location.href = '/logout';
  }
}
