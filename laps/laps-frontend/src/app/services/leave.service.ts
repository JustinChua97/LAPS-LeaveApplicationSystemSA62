import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EntitlementDto, LeaveDto } from '../models/leave.model';

@Injectable({ providedIn: 'root' })
export class LeaveService {

  private http = inject(HttpClient);

  getEntitlements(): Observable<EntitlementDto[]> {
    return this.http.get<EntitlementDto[]>('/api/v1/leaves/entitlements');
  }

  getMyLeaves(): Observable<LeaveDto[]> {
    return this.http.get<LeaveDto[]>('/api/v1/leaves/my');
  }
}
