# Suscribirse a un artículo

`POST /v1/subscriptions` requiere `Authorization: Bearer <token>` y permiso `user`.
La identidad se obtiene de Auth; un `userId` enviado en el cuerpo no determina el propietario.

```json
{
  "articleId": "507f1f77bcf86cd799439011",
  "channel": "SMS",
  "contact": "+54 9 (11) 2345-6789"
}
```

Los tres campos son obligatorios y de texto. Los canales admitidos son exactamente
`EMAIL`, `SMS` y `PUSH`. El ID se valida como ObjectId y se guarda en minúsculas.

- EMAIL: se recortan espacios exteriores y se valida con Jakarta Bean Validation `@Email`.
- SMS: se exige `+` con código de país; se permiten dígitos ASCII, espacios, guiones
  y paréntesis. Se rechazan letras, extensiones y números locales sin país.
  libphonenumber valida la estructura y normaliza a E.164: `+5491123456789`.
  Esto no verifica existencia, titularidad ni capacidad de recibir SMS.
- PUSH: se exige un identificador no vacío y se conserva exactamente como se recibió.
  No se integra un proveedor ni se verifica la validez del token en esta etapa.

El flujo valida datos, consulta duplicados activos, valida el artículo mediante Catalog
y finalmente inserta la suscripción. El índice único `unique_active_subscription`
también protege ante solicitudes simultáneas. Las suscripciones CANCELLED o NOTIFIED
no bloquean una nueva suscripción.

La respuesta es `201 Created` con `subscriptionId`, `articleId`, `userId`, `contact`,
`channel`, `status: SUBSCRIBED`, `createdAt`, `cancelledAt: null` y `notifiedAt: null`.

| HTTP | Código |
|---|---|
| 400 | MISSING_FIELDS, INVALID_CHANNEL, INVALID_ARTICLE_ID, INVALID_CONTACT |
| 400 | INVALID_REQUEST (JSON no interpretable) |
| 401 | MISSING_JWT, INVALID_JWT |
| 403 | ACCESS_DENIED |
| 404 | ARTICLE_NOT_FOUND (incluye artículo deshabilitado) |
| 409 | ALREADY_SUBSCRIBED, ARTICLE_HAS_STOCK |
| 503 | TIMEOUT_CATALOG, CATALOG_UNAVAILABLE, AUTH_UNAVAILABLE |
| 500 | INTERNAL_ERROR (fallas técnicas inesperadas) |

No se guarda una suscripción cuando falla la validación de Catalog. La comprobación
de stock refleja el momento de la consulta; no reserva stock ni hace una transacción
distribuida con Catalog.

El endpoint puede probarse desde Swagger UI en `/swagger-ui/index.html`. Las pruebas
automatizadas cubren HTTP con seguridad real y dependencias simuladas, además de
persistencia y concurrencia con MongoDB temporal mediante Testcontainers.
