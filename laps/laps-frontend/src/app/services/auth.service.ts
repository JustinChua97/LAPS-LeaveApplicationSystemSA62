import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, EMPTY } from 'rxjs';
import { tap, catchError } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private http = inject(HttpClient);

  private fullName    = '';
  private designation = '';

  // called by APP_INITIALIZER on startup, redirects to /login if session is gone
  fetchCurrentUser(): Promise<void> {
    const request$ = this.http.get<{ fullName: string; designation: string }>('/api/v1/me').pipe(
      tap(user => {
        this.fullName = user.fullName;
        this.designation = user.designation;
      }),
      catchError(() => {
        window.location.href = '/login';
        return EMPTY;
      })
    );
    return firstValueFrom(request$ as any) as Promise<void>;
  }

  isLoggedIn(): boolean {
    return this.fullName !== '';
  }

  getFullName(): string {
    return this.fullName;
  }

  getDesignation(): string {
    return this.designation;
  }
}
