import { UsuarioResumen } from './lead.model';

export interface Comision {
  id: number;
  leadId: number;
  leadNombreCliente: string;
  asesor: UsuarioResumen;
  monto: number;
  porcentajeAplicado: number;
  pagada: boolean;
  fechaCreacion: string;
  fechaPago: string | null;
}

export interface ComisionConfig {
  porcentaje: number;
}
