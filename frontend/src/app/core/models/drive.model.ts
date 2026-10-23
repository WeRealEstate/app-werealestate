/** Estado de la conexión con Google Drive para los archivos de Registrar venta (backend DriveService.Estado). */
export interface EstadoDrive {
  /** true si el servidor tiene las credenciales de Google (variables de entorno). */
  configurado: boolean;
  /** true si además tiene la clave del selector de Google (para elegir la carpeta). */
  selectorConfigurado: boolean;
  conectado: boolean;
  cuenta: string | null;
  conectadoEn: string | null;
  /** La carpeta existente que eligió el admin; null = falta elegirla y las subidas esperan. */
  carpetaNombre: string | null;
  carpetaUrl: string | null;
  /** Archivos que faltan por subir (nuevos o de registros anteriores). */
  pendientes: number;
  subidos: number;
  /** Archivos que fallaron demasiadas veces y esperan "Reintentar". */
  fallidos: number;
  ultimoError: string | null;
  ultimoErrorEn: string | null;
}

/** Lo que pide el selector de Google para abrirse (backend DriveService.DatosSelector). */
export interface DatosSelectorDrive {
  accessToken: string;
  apiKey: string;
  appId: string;
}
