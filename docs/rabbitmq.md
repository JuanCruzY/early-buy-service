# Contratos RabbitMQ de Early Buy

Todos los componentes utilizan el mismo broker y virtual host configurados en
application.properties. Las fechas se serializan en ISO-8601 UTC. Los IDs de artículos
y usuarios son cadenas hexadecimales.

## Validar artículos contra Catalog

| Elemento | Valor |
|---|---|
| Exchange | article_exist |
| Tipo / durable / autoDelete | direct / false / false |
| Routing key de consulta | article_exist |
| Cola de Catalog | catalog_article_exist |
| Respuesta | Cola exclusiva y temporal por instancia de Early Buy |

Early Buy publica este envoltorio, requerido por el servicio Go:

```json
{
  "correlation_id": "uuid-de-consulta",
  "exchange": "article_exist",
  "routing_key": "earlybuy.article.validation.uuid-de-instancia",
  "message": {
    "referenceId": "uuid-de-consulta",
    "articleId": "507f1f77bcf86cd799439011"
  }
}
```

El exchange y routing key físicos de la consulta son article_exist / article_exist.
Los campos exchange y routing_key del JSON indican a Catalog dónde responder.
La cola de respuestas se vincula al mismo exchange con una clave igual a su nombre.
Catalog devuelve su propio envoltorio:

```json
{
  "correlation_id": "uuid-de-consulta",
  "exchange": "",
  "routing_key": "",
  "message": {
    "referenceId": "uuid-de-consulta",
    "articleId": "507f1f77bcf86cd799439011",
    "valid": true,
    "stock": 0,
    "price": 100
  }
}
```

Early Buy correlaciona por referenceId del cuerpo. El publicador Go no reproduce las
propiedades AMQP correlationId y replyTo para esta consulta. valid=false produce
ARTICLE_NOT_FOUND. Con valid=true, stock menor o igual a cero permite el alta,
y stock positivo produce ARTICLE_HAS_STOCK. Ver [validación](catalog-validation.md).

## Recibir cambios de stock

| Elemento | Valor |
|---|---|
| Productor | Catalog |
| Exchange | catalog-events |
| Tipo / durable / autoDelete | fanout / true / false |
| Routing key publicada | catalog.stock_changed |
| Cola de Early Buy | earlybuy.catalog.stock_changed |
| Cola durable / exclusive / autoDelete | true / false / false |
| Consumidores por instancia / prefetch | 1 / 1 |
| Confirmación | AUTO de Spring, después del retorno exitoso del listener |

El cuerpo es JSON directo:

```json
{
  "eventId": "28f60287-820c-4515-893a-761e1d126182",
  "articleId": "507f1f77bcf86cd799439011",
  "currentStock": 25,
  "updatedAt": "2026-11-20T12:00:00Z"
}
```

Catalog publica mensajes persistentes con Content-Type application/json y
correlationId igual a eventId. El exchange fanout entrega una copia a cada cola
vinculada sin filtrar por routing key. El listener de Early Buy comprueba la clave.
Si falta correlationId, utiliza eventId. El JSON no tiene envoltorio message.

El listener valida UUID de eventId, formato ObjectId de articleId, stock presente y
fecha presente. Los eventos mal formados se registran y confirman. También se confirman
los eventos ajenos a catalog.stock_changed, los de stock no positivo y los que no
encuentran suscripciones activas. No generan una respuesta HTTP.

Para stock positivo se consulta por artículo y status SUBSCRIBED. Cada suscripción
se relee dentro del bloqueo compartido con cancelación. Se publica el aviso, se
espera confirmación y se actualizan status=NOTIFIED y notifiedAt condicionalmente.
No se agregan campos a los documentos de MongoDB.

Si un canal falla, se intenta continuar con las otras suscripciones. Al terminar,
el listener espera cinco segundos y solicita devolver el evento a la cola. Los
documentos ya NOTIFIED se saltean al reintentar. Los fallos técnicos de MongoDB
también provocan reintento. Una ruta ausente puede mantener el evento pendiente
hasta que se cree la cola y su binding.

## Publicar solicitudes de notificación

| Elemento | Valor |
|---|---|
| Exchange | notifications |
| Tipo / durable / autoDelete | direct / true / false |
| EMAIL | mail.notify |
| SMS | sms.notify |
| PUSH | push.notify |
| Content-Type / deliveryMode | application/json / PERSISTENT |
| correlationId | Se propaga desde el evento de Catalog |
| messageId | subscriptionId, estable en cada reintento |

```json
{
  "articleId": "507f1f77bcf86cd799439011",
  "userId": "507f1f77bcf86cd799439013",
  "currentStock": 25,
  "contact": "usuario@correo.com"
}
```

El canal determina la routing key. Los consumidores de correo, SMS y PUSH deben
declarar sus colas y bindings. Para conservar mensajes ante reinicios, sus colas
deben ser durables. Early Buy declara el exchange y utiliza mandatory=true,
publisher confirms correlacionados y publisher returns.

El CompletableFuture<Void> se completa correctamente cuando RabbitMQ confirma la
publicación sin devolución por falta de ruta. Los errores de conexión, nack,
devolución o vencimiento del plazo completan el futuro con NotificationPublishException.
La confirmación acredita aceptación por RabbitMQ, no la entrega final al usuario.

## Duplicados y operaciones simultáneas

Si el proceso cae después de publicar y antes de guardar NOTIFIED, puede reenviar
el aviso. messageId permite que el futuro consumidor reconozca el duplicado.
correlationId se comparte entre todos los avisos de una reposición y no alcanza
por sí solo para deduplicar por suscripción.

SubscriptionOperationLock coordina cancelación y envío dentro de una instancia.
Si gana cancelación, se omite el aviso. Si se completa el envío y NOTIFIED primero,
la cancelación devuelve INVALID_STATUS_TRANSITION. Los bloqueos no se comparten
entre distintas instancias.

No se guarda un historial de eventId procesados. Un evento repetido o tardío puede
alcanzar una suscripción nueva que esté activa al momento de procesarlo. El flujo
evalúa las suscripciones existentes cuando recibe el mensaje. Tampoco consulta
nuevamente el stock al crear la suscripción ni ejecuta revisiones periódicas.
