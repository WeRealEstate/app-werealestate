import { DatosSelectorDrive } from '../models/drive.model';

/* El selector oficial de Google Drive (Google Picker). Con el permiso drive.file la app solo puede escribir en una
 * carpeta existente si el usuario la elige aquí: al elegirla, Google se la autoriza a esta app (por eso va el App ID). */

// La librería de Google se carga en tiempo de ejecución y no trae tipos.
declare const gapi: { load(api: string, opciones: { callback: () => void; onerror: () => void }): void };
declare const google: any; // eslint-disable-line @typescript-eslint/no-explicit-any

const SCRIPT = 'https://apis.google.com/js/api.js';
let cargando: Promise<void> | null = null;

function cargarSelector(): Promise<void> {
  if (!cargando) {
    cargando = new Promise<void>((resolve, reject) => {
      const listo = () => gapi.load('picker', { callback: () => resolve(), onerror: () => reject(new Error('picker')) });
      if (typeof gapi !== 'undefined') {
        listo();
        return;
      }
      const s = document.createElement('script');
      s.src = SCRIPT;
      s.async = true;
      s.onload = listo;
      s.onerror = () => reject(new Error('script'));
      document.head.appendChild(s);
    }).catch((e) => {
      cargando = null; // que el siguiente intento vuelva a probar
      throw e;
    });
  }
  return cargando;
}

/** Abre el selector solo con carpetas. Resuelve con la carpeta elegida o null si se cerró sin elegir. */
export async function elegirCarpetaDeDrive(datos: DatosSelectorDrive): Promise<{ id: string; nombre: string } | null> {
  await cargarSelector();
  return new Promise((resolve) => {
    const vista = new google.picker.DocsView(google.picker.ViewId.FOLDERS)
      .setIncludeFolders(true)
      .setSelectFolderEnabled(true)
      .setMimeTypes('application/vnd.google-apps.folder');
    const selector = new google.picker.PickerBuilder()
      .setTitle('Elige la carpeta donde van los expedientes')
      .setLocale('es')
      .setOAuthToken(datos.accessToken)
      .setDeveloperKey(datos.apiKey)
      .setAppId(datos.appId)
      .addView(vista)
      .setCallback((r: { action: string; docs?: { id: string; name: string }[] }) => {
        if (r.action === google.picker.Action.PICKED && r.docs?.length) {
          resolve({ id: r.docs[0].id, nombre: r.docs[0].name });
        } else if (r.action === google.picker.Action.CANCEL) {
          resolve(null);
        }
      })
      .build();
    selector.setVisible(true);
  });
}
