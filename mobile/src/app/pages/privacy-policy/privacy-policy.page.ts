import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-privacy-policy',
  templateUrl: './privacy-policy.page.html',
  styleUrls: ['./privacy-policy.page.scss'],
  standalone: false,
})
export class PrivacyPolicyPage implements OnInit {
  readonly email = 'compregado@appcompregado.com.br';

  constructor(private titulo: Title) {}

  ngOnInit(): void {
    this.titulo.setTitle('Política de Privacidade — CompreGado');
  }
}
