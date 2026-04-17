import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

// The root component is just a shell.
// <router-outlet> is where Angular renders whichever component matches the current URL.
// Equivalent to a Thymeleaf layout's fragment slot.
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
