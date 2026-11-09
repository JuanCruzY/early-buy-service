# Validación de artículos con Catalog

`CatalogValidationService.validateForSubscription(articleId)` valida el formato del ID,
lo normaliza a hexadecimal en minúsculas y consulta Catalog por RabbitMQ.
Devuelve `CatalogArticleStatus` si el artículo está habilitado y su stock es menor o igual a cero.
El caso de uso de creación de suscripciones utilizará este servicio antes de guardar.

## Contrato Go

La consulta se publica en el exchange `article_exist` (direct, no durable),
con routing key `article_exist`. El JSON contiene la dirección de respuesta:

```json
{
  "correlation_id": "uuid-de-la-consulta",
  "exchange": "article_exist",
  "routing_key": "earlybuy.article.validation.uuid-de-la-instancia",
  "message": {
    "referenceId": "uuid-de-la-consulta",
    "articleId": "507f1f77bcf86cd799439011"
  }
}
```

Cada instancia declara una cola exclusiva, no durable y con eliminación automática,
vinculada a `article_exist` mediante una routing key igual al nombre de esa cola.
La cola recibe todas las respuestas de esa instancia. `referenceId` identifica cada consulta.
Los campos `exchange` y `routing_key` del cuerpo indican a Catalog dónde publicar la respuesta;
RabbitMQ no interpreta esos campos del JSON.

El listener decodifica el envoltorio Go y los campos `articleId`, `referenceId`, `valid` y `stock`
de `message`. No requiere headers AMQP de correlación ni Content-Type en la respuesta.
Campos adicionales, como `price`, no intervienen en la validación.

## Errores

| Código | HTTP | Condición |
|---|---|---|
| `INVALID_ARTICLE_ID` | 400 | ID sin formato ObjectId hexadecimal. |
| `ARTICLE_NOT_FOUND` | 404 | Catalog responde `valid=false`. |
| `ARTICLE_HAS_STOCK` | 409 | Catalog responde `valid=true` y `stock>0`. |
| `TIMEOUT_CATALOG` | 503 | No se recibe una respuesta válida dentro del plazo. |
| `CATALOG_UNAVAILABLE` | 503 | Falla de publicación, mensaje sin destino, respuesta correlacionada inválida, interrupción, cierre o capacidad agotada. |

Catalog actualmente responde `valid=false` ante cualquier error de búsqueda, incluidas
fallas de su base de datos. Ese contrato no permite distinguirlas de un artículo inexistente.
Una respuesta sin correlación utilizable se descarta; la consulta correspondiente, si existe,
terminará por timeout. Las respuestas tardías o duplicadas no modifican resultados terminados.

## Configuración

| Variable | Valor predeterminado |
|---|---|
| `CATALOG_VALIDATION_TIMEOUT` | `5s` |
| `CATALOG_MAX_PENDING_REQUESTS` | `256` |
| `RABBITMQ_CONNECTION_TIMEOUT` | `3s` |
| `RABBITMQ_CHANNEL_RPC_TIMEOUT` | `3s` |

La espera incluye el tiempo en la cola de publicación. La publicación se ejecuta en un pool
acotado para que una conexión lenta no bloquee el hilo HTTP más allá del plazo de validación.
Las confirmaciones del broker y `mandatory` permiten detectar mensajes rechazados o sin destino.
Estas confirmaciones no reemplazan la respuesta de Catalog.

## Pruebas

Con Docker disponible, `./gradlew.bat test` ejecuta las pruebas unitarias y las integraciones
con contenedores temporales. No usa las bases del eCommerce.

Para incluir la prueba con el servicio Go real, primero debe existir una imagen local
de Catalog construida desde su repositorio. En PowerShell:

```powershell
$env:CATALOG_GO_IMAGE = 'prod-cataloggo-go'
.\gradlew.bat test --tests '*CatalogGoCompatibilityTests'
```

Esa prueba utiliza su propia red, MongoDB, RabbitMQ y Catalog; no necesita Auth.
Al terminar se eliminan los contenedores temporales.
