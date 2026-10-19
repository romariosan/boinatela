import { Component } from '@angular/core';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-landing',
  templateUrl: './landing.page.html',
  styleUrls: ['./landing.page.scss'],
  standalone: false,
})
export class LandingPage {
  readonly playUrl = environment.googlePlayUrl;
  readonly email = 'compregado@appcompregado.com.br';
}
