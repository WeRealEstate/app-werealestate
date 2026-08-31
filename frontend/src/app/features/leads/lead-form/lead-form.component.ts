import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LeadsService } from '../../../core/services/leads.service';
import { Desarrollo } from '../../../core/models/lead.model';

@Component({
  selector: 'app-lead-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './lead-form.component.html',
})
export class LeadFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly leadsService = inject(LeadsService);
  private readonly router = inject(Router);

  readonly desarrollos = signal<Desarrollo[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.group({
    nombreCliente: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    telefono: this.fb.control('', { nonNullable: true, validators: [Validators.required] }),
    email: this.fb.control('', { nonNullable: true, validators: [Validators.email] }),
    origen: this.fb.control('', { nonNullable: true }),
    desarrolloId: this.fb.control<number | null>(null, { validators: [Validators.required] }),
    valorEstimado: this.fb.control<number | null>(null),
  });

  async ngOnInit(): Promise<void> {
    this.desarrollos.set(await this.leadsService.listarDesarrollos());
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    const v = this.form.getRawValue();

    try {
      const lead = await this.leadsService.crear({
        nombreCliente: v.nombreCliente,
        telefono: v.telefono,
        email: v.email || null,
        origen: v.origen || null,
        desarrolloId: v.desarrolloId!,
        valorEstimado: v.valorEstimado,
      });
      await this.router.navigate(['/panel/leads', lead.id]);
    } catch {
      this.errorMessage.set('No se pudo crear el lead. Intenta de nuevo.');
    } finally {
      this.isLoading.set(false);
    }
  }
}
