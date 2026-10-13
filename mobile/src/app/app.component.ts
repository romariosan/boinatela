import { Component } from '@angular/core';
import { addIcons } from 'ionicons';
import {
  addCircleOutline,
  arrowBackOutline,
  cameraOutline,
  checkmark,
  chevronDownOutline,
  close,
  createOutline,
  logOutOutline,
  logoWhatsapp,
  personOutline,
  pricetagOutline,
  refreshOutline,
  searchOutline,
  shieldCheckmarkOutline,
  storefrontOutline,
  trashOutline,
} from 'ionicons/icons';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: false,
})
export class AppComponent {
  constructor() {
    addIcons({
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
    });
  }
}
