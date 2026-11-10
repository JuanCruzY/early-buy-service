# Consultar suscripciones

`GET /v1/subscriptions` requiere `Authorization: Bearer <token>` y permiso `user`.
No recibe cuerpo ni parámetros. El usuario se obtiene de Auth y la consulta a MongoDB
filtra por ese `userId` y por estado `SUBSCRIBED`. No se consulta Catalog.

Responde `200 OK` con una lista de objetos que contienen únicamente `subscriptionId`,
`articleId`, `channel` y `contact`. No se garantiza un orden específico.

```json
[
  {
    "subscriptionId": "507f1f77bcf86cd799439012",
    "articleId": "507f1f77bcf86cd799439011",
    "channel": "EMAIL",
    "contact": "usuario@correo.com"
  }
]
```

Si no hay resultados, responde `200 OK` con `[]`. Este comportamiento fue acordado
para resolver el camino alternativo sin respuesta HTTP definida en la documentación original.
El cliente puede mostrar el mensaje de que no hay suscripciones activas.

Las suscripciones `CANCELLED` y `NOTIFIED`, y las de otros usuarios, no se incluyen.
La seguridad conserva los errores `401 MISSING_JWT`, `401 INVALID_JWT`,
`403 ACCESS_DENIED` y `503 AUTH_UNAVAILABLE`. Las fallas técnicas inesperadas
usan el formato común `500 INTERNAL_ERROR`.
