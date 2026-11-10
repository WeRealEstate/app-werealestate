package com.werealestate.backend.model;

/**
 * Cómo va un pago de la tabla de amortización (ver service/CalendarioPagos). PAGADO: ya lo cubre lo abonado.
 * ATRASADO: su fecha ya pasó y no está cubierto (cuenta desde el día siguiente). LE_TOCA: el siguiente pago sin
 * cubrir que aún no vence (todos los de esa misma fecha). PENDIENTE: los que siguen. ABONADO: solo en la tabla
 * mes con mes (service/TablaMensual): ese mes recibió algún pago, pero no toda la mensualidad.
 */
public enum EstadoPagoPlan {
    PAGADO,
    ATRASADO,
    LE_TOCA,
    PENDIENTE,
    ABONADO
}
