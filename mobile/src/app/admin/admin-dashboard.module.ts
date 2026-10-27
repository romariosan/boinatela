import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { IonicModule } from '@ionic/angular/lazy';
import { adminGuard } from '../guards/admin.guard';
import { AdminDashboardPage } from './admin-dashboard.page';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    RouterModule.forChild([
      {
        path: '',
        component: AdminDashboardPage,
        canActivate: [adminGuard],
      },
    ]),
  ],
  declarations: [AdminDashboardPage],
})
export class AdminDashboardPageModule {}
