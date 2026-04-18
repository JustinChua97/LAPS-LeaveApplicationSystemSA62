import { Component, OnInit, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { forkJoin } from 'rxjs';
import { LeaveService } from '../../services/leave.service';
import { AuthService } from '../../services/auth.service';
import { EntitlementDto, LeaveDto } from '../../models/leave.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [NgClass],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {

  private leaveService = inject(LeaveService);
  protected authService = inject(AuthService);

  fullName = this.authService.getFullName();
  designation = this.authService.getDesignation();

  entitlements = signal<EntitlementDto[]>([]);
  recentLeaves = signal<LeaveDto[]>([]);
  loading = signal(true);
  error = signal('');

  ngOnInit(): void {
    forkJoin({
      entitlements: this.leaveService.getEntitlements(),
      leaves: this.leaveService.getMyLeaves(),
    }).subscribe({
      next: ({ entitlements, leaves }) => {
        this.entitlements.set(entitlements);
        this.recentLeaves.set(leaves.slice(0, 5));
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load dashboard. Please refresh the page.');
        this.loading.set(false);
      },
    });
  }
}
