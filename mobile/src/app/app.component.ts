import { Component } from '@angular/core';
import { addIcons } from 'ionicons';
import {
  add,
  addCircleOutline,
  alertCircleOutline,
  arrowBackOutline,
  cameraOutline,
  checkmark,
  chevronDownOutline,
  close,
  cloudOfflineOutline,
  cloudUploadOutline,
  createOutline,
  flagOutline,
  imagesOutline,
  layersOutline,
  locationOutline,
  lockClosedOutline,
  logOutOutline,
  logoWhatsapp,
  moonOutline,
  pawOutline,
  personOutline,
  pricetagOutline,
  refreshOutline,
  ribbonOutline,
  scaleOutline,
  searchOutline,
  shareSocialOutline,
  shieldCheckmarkOutline,
  storefrontOutline,
  sunnyOutline,
  trashOutline,
} from 'ionicons/icons';
import { Observable } from 'rxjs';
import { ConexaoService } from './services/conexao.service';
import { FilaPublicacaoService } from './services/fila-publicacao.service';
import { TemaService } from './services/tema.service';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: false,
})
export class AppComponent {
  readonly online$: Observable<boolean>;

  // TemaService entra aqui só para aplicar o modo sol salvo antes da primeira tela.
  constructor(conexao: ConexaoService, _tema: TemaService, fila: FilaPublicacaoService) {
    this.online$ = conexao.online$;
    fila.iniciar();
    addIcons({
      add,
      storefrontOutline,
      searchOutline,
      personOutline,
      cameraOutline,
      close,
      logoWhatsapp,
      addCircleOutline,
      logOutOutline,
      checkmark,
      chevronDownOutline,
      createOutline,
      pricetagOutline,
      refreshOutline,
      trashOutline,
      shieldCheckmarkOutline,
      arrowBackOutline,
      alertCircleOutline,
      cloudOfflineOutline,
      flagOutline,
      imagesOutline,
      layersOutline,
      locationOutline,
      pawOutline,
      ribbonOutline,
      scaleOutline,
      shareSocialOutline,
      sunnyOutline,
      moonOutline,
      cloudUploadOutline,
      lockClosedOutline,
    });
  }
}
