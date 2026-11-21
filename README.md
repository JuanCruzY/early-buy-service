# Early Buy

Microservicio Java 25, Spring Boot y Gradle para suscribirse a artículos sin stock.
Guarda las suscripciones en MongoDB, valida usuarios mediante Auth y artículos
mediante Catalog, y publica solicitudes de notificación en RabbitMQ.

## Requisitos

- Para IntelliJ o Gradle local, JDK 25 y acceso a Maven Central para descargar dependencias.
- Para contenedores, Docker Desktop con contenedores Linux y Docker Compose v2.
- MongoDB 6, RabbitMQ 3.13 y los servicios Auth y Catalog del eCommerce disponibles.
  Catalog debe contener la publicación de `catalog.stock_changed` que agregamos.
- Para completar NOTIFIED, una cola consumidora debe estar vinculada al canal elegido
  en `notifications`. El exchange por sí solo no almacena mensajes.

## Ejecutar desde IntelliJ IDEA

1. Abrir este directorio como proyecto Gradle y seleccionar JDK 25 tanto para el
   proyecto como para Gradle.
2. Levantar MongoDB, RabbitMQ, Auth y Catalog con sus puertos publicados en el host.
3. Ejecutar `EarlyBuyServiceApplication` desde IntelliJ. También se puede usar PowerShell:

```powershell
.\gradlew.bat bootRun
```

Los valores locales predeterminados apuntan a `localhost`. La aplicación usa el
puerto 3005 y la base `earlybuy`. Las variables se configuran en la ejecución de
IntelliJ o en PowerShell antes de arrancar. El archivo `.env` se utiliza por Compose,
y no se carga automáticamente al ejecutar desde IntelliJ o Gradle.

## Ejecutar con Docker Compose

Desde este directorio, con las dependencias ya disponibles en los puertos del host:

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs -f early-buy
```

Compose construye la imagen `early-buy-service:local` y publica el puerto 3005.
Dentro del contenedor, MongoDB, RabbitMQ y Auth se alcanzan por
`host.docker.internal`. Catalog se consulta por RabbitMQ.
El mapeo `host-gateway` permite resolver ese nombre también en Docker Engine Linux,
siempre que los puertos de las dependencias sean accesibles desde el contenedor.

Para personalizar la conexión:

```powershell
Copy-Item .env.example .env
# Editar .env y luego ejecutar docker compose up -d --build
```

El archivo `.env` queda fuera de Git y del contexto Docker. El ejemplo contiene las
credenciales guest del entorno local. Se pueden sustituir por las del broker.
Las variables existentes de PowerShell prevalecen sobre las del archivo `.env`.
Para usar otro puerto del host, establecer `EARLYBUY_PORT=3015` y acceder por 3015.
El contenedor sigue escuchando en 3005. IntelliJ y Docker necesitan puertos de host
distintos si se ejecutan al mismo tiempo.

Para aplicar cambios de código y luego detener Early Buy:

```powershell
docker compose up -d --build
docker compose down
```

`down` elimina los contenedores y la red de este Compose. Los datos están en el
MongoDB externo y se conservan.

## Construir y ejecutar sin Compose

```powershell
docker build -t early-buy-service:local .
docker run -d --name early-buy-service -p 3005:3005 --add-host host.docker.internal:host-gateway -e MONGODB_URI=mongodb://host.docker.internal:27017/earlybuy -e RABBITMQ_HOST=host.docker.internal -e AUTH_BASE_URL=http://host.docker.internal:3000 early-buy-service:local
docker logs --tail 100 early-buy-service
```

El Dockerfile usa el wrapper de Gradle para compilar el JAR y Java 25 para ejecutarlo
con un usuario sin privilegios. La construcción necesita acceso a los repositorios
de imágenes y dependencias. Las pruebas se ejecutan aparte de la construcción.

## API y autenticación

- Swagger UI está en [http://localhost:3005/swagger-ui/index.html](http://localhost:3005/swagger-ui/index.html).
- OpenAPI JSON está en [http://localhost:3005/v3/api-docs](http://localhost:3005/v3/api-docs).
- Salud está en [http://localhost:3005/actuator/health](http://localhost:3005/actuator/health).

Auth expone `POST http://localhost:3000/users/signin`. Desde PowerShell, con una
cuenta existente del eCommerce:

```powershell
$credentials = Get-Credential -Message 'Login y contraseña de Auth'
$body = @{ login = $credentials.UserName; password = $credentials.GetNetworkCredential().Password } | ConvertTo-Json
$session = Invoke-RestMethod -Method Post -Uri http://localhost:3000/users/signin -ContentType application/json -Body $body
$headers = @{ Authorization = "Bearer " + $session.token }
Invoke-RestMethod -Uri http://localhost:3005/v1/subscriptions -Headers $headers
```

En Swagger, ingresar el valor de `token` en **Authorize**, sin escribir `Bearer`.
Early Buy consulta a Auth por petición y exige el permiso `user`.
Auth actualmente no asigna expiración al JWT. Su cierre de sesión invalida el token
y Early Buy respeta esa validación.

| Operación | Resultado |
|---|---|
| POST /v1/subscriptions | 201 con la suscripción creada |
| GET /v1/subscriptions | 200 con cuatro campos por suscripción activa o [] |
| DELETE /v1/subscriptions/{subscriptionId} | 204 sin cuerpo |

Los contratos completos y errores están en [creación](docs/create-subscription.md),
[consulta](docs/list-subscriptions.md), [cancelación](docs/cancel-subscription.md)
y Swagger. Los errores usan `timestamp`, `status`, `code`, `message` y `path`.
Los IDs numéricos de la documentación original eran ilustrativos. La implementación
usa IDs ObjectId hexadecimales de texto.

## Configuración

| Variable | IntelliJ / Gradle | Compose |
|---|---|---|
| SERVER_PORT | 3005 | 3005 interno |
| EARLYBUY_PORT | No se utiliza | 3005 del host |
| MONGODB_URI | mongodb://localhost:27017/earlybuy | mongodb://host.docker.internal:27017/earlybuy |
| RABBITMQ_HOST | localhost | host.docker.internal |
| RABBITMQ_PORT | 5672 | 5672 |
| RABBITMQ_USERNAME / RABBITMQ_PASSWORD | guest / guest | guest / guest |
| RABBITMQ_VIRTUAL_HOST | / | / |
| AUTH_BASE_URL | http://localhost:3000 | http://host.docker.internal:3000 |
| AUTH_CONNECT_TIMEOUT / AUTH_READ_TIMEOUT | 1s / 3s | 1s / 3s |
| RABBITMQ_CONNECTION_TIMEOUT / RABBITMQ_CHANNEL_RPC_TIMEOUT | 3s / 3s | 3s / 3s |
| CATALOG_VALIDATION_TIMEOUT | 5s | 5s |
| CATALOG_MAX_PENDING_REQUESTS | 256 | 256 |
| NOTIFICATION_CONFIRMATION_TIMEOUT | 5s | 5s |

Actuator informa la conectividad con MongoDB y RabbitMQ. Un resultado UP no comprueba
la disponibilidad HTTP de Auth, las respuestas de Catalog ni la entrega de notificaciones.
Compose marca el contenedor healthy cuando ese endpoint responde correctamente.

## RabbitMQ y comprobación funcional

Los [contratos RabbitMQ](docs/rabbitmq.md) incluyen la validación Go, el consumo de stock,
las propiedades del mensaje y el comportamiento de reintentos.

Para probar el flujo en el entorno local, crear un artículo real sin stock en Catalog,
crear una suscripción mediante Swagger y reponer el artículo. Debe existir la cola del
canal elegido y estar vinculada a `notifications`. Early Buy marcará NOTIFIED cuando
RabbitMQ confirme la publicación. El servicio que entrega correo, SMS o PUSH queda
pendiente de desarrollo. Mientras no exista la ruta, Early Buy reintenta.

Los bloqueos entre envío y cancelación se comparten dentro de un proceso. Usar una
instancia para conservar esa coordinación. Si el proceso cae después de publicar y
antes de guardar NOTIFIED, puede repetir el aviso con el mismo messageId. El consumidor
de notificaciones deberá reconocer duplicados.

El alta hace una sola consulta a Catalog antes de guardar. Si una reposición se procesa
justo antes de guardar el alta, esa suscripción podrá procesarse en un evento positivo
posterior. No hay una revisión periódica de stock.

## Pruebas

Con JDK 25 y Docker disponibles:

```powershell
.\gradlew.bat test
```

Las integraciones crean MongoDB y RabbitMQ temporales y aislados. La prueba opcional
con Catalog Go puede habilitarse con una imagen local actualizada:

```powershell
$env:CATALOG_GO_IMAGE = 'cataloggo-stock-events-test:latest'
.\gradlew.bat test --tests '*CatalogGoCompatibilityTests'
Remove-Item Env:CATALOG_GO_IMAGE
```
