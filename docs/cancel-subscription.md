# Cancelar suscripción

`DELETE /v1/subscriptions/{subscriptionId}` requiere Bearer y permiso `user`.
No requiere cuerpo. Solo el propietario puede cancelar una suscripción `SUBSCRIBED`.

Se conserva el documento y se actualizan juntos `status: CANCELLED` y `cancelledAt`.
La escritura mediante MongoTemplate exige que coincidan ID, propietario y estado
`SUBSCRIBED`; no se usa `save()` para evitar sobrescribir cambios concurrentes.
El modelo prepara la transición y la fecha; el fragmento de repositorio realiza
la actualización condicional. Si no actualiza, el servicio relee para distinguir
un documento inexistente, un propietario distinto o un conflicto de estado.

| HTTP | Resultado |
|---|---|
| 204 | Cancelada; sin cuerpo de respuesta |
| 401 | MISSING_JWT / INVALID_JWT |
| 403 | NOT_OWNER / ACCESS_DENIED |
| 404 | SUBSCRIPTION_NOT_FOUND (incluye ID con formato inválido) |
| 409 | INVALID_STATUS_TRANSITION (CANCELLED, NOTIFIED o transición concurrente) |
| 503 | AUTH_UNAVAILABLE |

La cancelación repetida devuelve 409 y no cambia la fecha original. Tras cancelar,
la suscripción desaparece de GET de activas y se permite una nueva para el mismo
artículo y usuario. No se consulta Catalog ni se publica un mensaje de cancelación.

La condición protege el estado en MongoDB; la coordinación con la publicación de
notificaciones en RabbitMQ se resolverá al implementar ese flujo.
