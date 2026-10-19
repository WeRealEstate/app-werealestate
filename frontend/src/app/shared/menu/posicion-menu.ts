import { ConnectedPosition } from '@angular/cdk/overlay';

/** Dónde se abre un menú desplegable ("Más", "⋯") respecto a su botón: debajo y alineado a la derecha
 * (los botones de acciones suelen estar pegados al borde derecho de la pantalla); si no cabe abajo,
 * se abre hacia arriba. Estilos del panel en styles.css (.menu-panel / .menu-item). */
export const POSICION_MENU_DERECHA: ConnectedPosition[] = [
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
];
