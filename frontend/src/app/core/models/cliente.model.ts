import { UbicacionDocumento } from './asesor-externo.model';

export type EstadoCivil = 'SOLTERO' | 'CASADO' | 'UNION_LIBRE' | 'DIVORCIADO' | 'VIUDO';
export const ESTADO_CIVIL_LABELS: Record<EstadoCivil, string> = {
  SOLTERO: 'Soltero(a)',
  CASADO: 'Casado(a)',
  UNION_LIBRE: 'Unión libre',
  DIVORCIADO: 'Divorciado(a)',
  VIUDO: 'Viudo(a)',
};

export type FuenteCliente = 'REDES_SOCIALES' | 'RECOMENDACION' | 'EVENTO' | 'ESPECTACULAR' | 'OTRO';
export const FUENTE_CLIENTE_LABELS: Record<FuenteCliente, string> = {
  REDES_SOCIALES: 'Redes sociales',
  RECOMENDACION: 'Recomendación',
  EVENTO: 'Evento',
  ESPECTACULAR: 'Espectacular',
  OTRO: 'Otro',
};

/** Una fila de la lista de clientes y del buscador de la venta. */
export interface ClienteLista {
  id: number;
  nombreCompleto: string;
  telefono: string | null;
  correo: string | null;
  activo: boolean;
  datosIncompletos: boolean;
  compras: number;
  desarrollos: string[];
  saldoPendiente: number;
}

export interface ClienteVenta {
  ventaId: number;
  numero: number;
  fechaVenta: string;
  desarrollos: string[];
  lotes: string[];
  precio: number;
  abonado: number;
  saldo: number;
  /** true si es copropietario de esa venta (no el cliente principal). */
  copropietario: boolean;
}

export interface Cliente {
  id: number;
  nombre: string;
  apellidoPaterno: string;
  apellidoMaterno: string | null;
  nombreCompleto: string;
  fechaNacimiento: string | null;
  edad: number | null;
  telefono: string | null;
  telefono2: string | null;
  correo: string | null;
  curp: string | null;
  rfc: string | null;
  lugarNacimiento: string | null;
  nacionalidad: string | null;
  estadoCivil: EstadoCivil | null;
  ocupacion: string | null;
  calle: string | null;
  colonia: string | null;
  municipio: string | null;
  estado: string | null;
  codigoPostal: string | null;
  beneficiarioNombre: string | null;
  beneficiarioParentesco: string | null;
  fuente: FuenteCliente | null;
  fuenteDetalle: string | null;
  captadoPorTipo: 'USUARIO' | 'ASESOR' | null;
  captadoPorId: number | null;
  captadoPorNombre: string | null;
  notas: string | null;
  expedienteUbicacion: UbicacionDocumento | null;
  expedienteDriveUrl: string | null;
  activo: boolean;
  datosIncompletos: boolean;
  fechaCreacion: string;
  compras: number;
  totalComprado: number;
  totalAbonado: number;
  saldoPendiente: number;
  ventas: ClienteVenta[];
}

export interface ClienteRequest {
  nombre: string;
  apellidoPaterno: string;
  apellidoMaterno: string | null;
  fechaNacimiento: string;
  telefono: string;
  telefono2: string | null;
  correo: string | null;
  curp: string | null;
  rfc: string | null;
  lugarNacimiento: string | null;
  nacionalidad: string | null;
  estadoCivil: EstadoCivil | null;
  ocupacion: string | null;
  calle: string | null;
  colonia: string | null;
  municipio: string | null;
  estado: string | null;
  codigoPostal: string | null;
  beneficiarioNombre: string | null;
  beneficiarioParentesco: string | null;
  fuente: FuenteCliente | null;
  fuenteDetalle: string | null;
  captadoPorUsuarioId: number | null;
  captadoPorAsesorId: number | null;
  notas: string | null;
  expedienteUbicacion: UbicacionDocumento | null;
  expedienteDriveUrl: string | null;
  /** null = sin cambios al editar. */
  activo: boolean | null;
}
