# Formalda

Formalda — серверный форум на Java 21 и Spring Boot 4. Интерфейс формируется Thymeleaf и работает через обычные ссылки и HTML-формы. В приложении есть категории, темы и ответы, поиск, реакции, закладки, подписки, уведомления, жалобы, модерация и административная панель. Интерфейс доступен на русском и английском языках.

## Стек

- Java 21, Gradle 8.14, Spring Boot 4.1.2;
- Spring MVC, Thymeleaf, Spring Security, Spring Data JPA и Bean Validation;
- MariaDB 11.4 и миграции Flyway;
- Mailpit для просмотра писем при локальной разработке;
- Testcontainers и MockMvc для интеграционных тестов.

## Быстрый запуск в Docker

Потребуются Docker Engine и Docker Compose v2.

1. Создайте локальный файл настроек:

   ```bash
   cp .env.example .env
   ```

2. Замените в `.env` значения `DB_PASSWORD`, `DB_ROOT_PASSWORD` и `FORMALDA_ADMIN_PASSWORD`. Для окружения, доступного из сети, также измените email и имя администратора.

3. Соберите и запустите приложение:

   ```bash
   docker compose up --build
   ```

После запуска доступны:

- форум: <http://localhost:8080>;
- Mailpit: <http://localhost:8025>;
- MariaDB: `localhost:3306`.

Порты MariaDB и Mailpit привязаны к `127.0.0.1`, поэтому база и письма с кодами подтверждения не публикуются в локальную сеть. HTTP-порт форума доступен на сетевых интерфейсах хоста; при публичном размещении используйте HTTPS-терминацию и обратный прокси.

Compose ожидает готовности MariaDB, после чего запускает приложение. Flyway применяет миграции автоматически. Схема JPA только проверяется, поэтому таблицы следует менять новой миграцией в `src/main/resources/db/migration`.

Остановка контейнеров:

```bash
docker compose down
```

Данные базы хранятся в именованном томе `formalda_mariadb-data`. Команда `docker compose down -v` также удалит этот том и все данные форума.

## Первый администратор

При первом запуске приложение создаёт администратора из переменных:

- `FORMALDA_ADMIN_EMAIL`;
- `FORMALDA_ADMIN_USERNAME`;
- `FORMALDA_ADMIN_PASSWORD`.

Начальная запись создаётся только тогда, когда в базе ещё нет администратора. Последующая смена переменных не изменяет существующую учётную запись. Пароль хранится в кодированном виде средствами Spring Security.

## Почта и подтверждение регистрации

В Docker-окружении приложение по умолчанию отправляет письма в Mailpit. Откройте <http://localhost:8025>, чтобы получить шестизначный код подтверждения регистрации. Mailpit не доставляет письма во внешнюю сеть.

Для настоящего SMTP-сервера измените `.env`, например:

```dotenv
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=mailer@example.com
SMTP_PASSWORD=replace-with-a-secret
SMTP_AUTH=true
SMTP_STARTTLS=true
FORMALDA_MAIL_FROM=forum@example.com
FORMALDA_BASE_URL=https://forum.example.com
```

После изменения пересоздайте контейнер приложения:

```bash
docker compose up -d --build app
```

## Переменные окружения

| Переменная | Назначение | Значение по умолчанию |
| --- | --- | --- |
| `APP_PORT` | HTTP-порт на хосте | `8080` |
| `FORMALDA_BASE_URL` | Публичный адрес для ссылок приложения | `http://localhost:8080` |
| `DB_PORT` | Порт MariaDB на хосте | `3306` |
| `DB_NAME` | Имя базы | `formalda` |
| `DB_USER` | Пользователь базы | `formalda` |
| `DB_PASSWORD` | Пароль пользователя базы | обязателен |
| `DB_ROOT_PASSWORD` | Пароль root MariaDB | обязателен |
| `SMTP_HOST`, `SMTP_PORT` | SMTP-сервер приложения | `mailpit`, `1025` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Учётные данные SMTP | пустые |
| `SMTP_AUTH` | Включить SMTP-аутентификацию | `false` |
| `SMTP_STARTTLS` | Включить STARTTLS | `false` |
| `FORMALDA_MAIL_FROM` | Адрес отправителя писем подтверждения | `noreply@formalda.local` |
| `MAILPIT_SMTP_PORT` | SMTP-порт Mailpit на хосте | `1025` |
| `MAILPIT_UI_PORT` | Веб-интерфейс Mailpit | `8025` |
| `FORMALDA_ADMIN_*` | Данные первого администратора | см. `.env.example` |

Файл `.env` исключён из Git. Не добавляйте настоящие пароли в Compose, исходный код или `.env.example`.

## Локальный запуск без контейнера приложения

Потребуются JDK 21 и работающий Docker. MariaDB и Mailpit можно запустить отдельно:

```bash
cp .env.example .env
docker compose up -d mariadb mailpit
set -a
. ./.env
set +a
export SPRING_DATASOURCE_URL="jdbc:mariadb://localhost:${DB_PORT}/${DB_NAME}"
export SPRING_DATASOURCE_USERNAME="${DB_USER}"
export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"
export SPRING_MAIL_HOST=localhost
export SPRING_MAIL_PORT="${MAILPIT_SMTP_PORT}"
./gradlew bootRun
```

## Сборка и тесты

```bash
./gradlew clean test
./gradlew clean build
```

Интеграционные тесты используют MariaDB через Testcontainers, поэтому для них должен быть запущен Docker. Отдельная сборка образа:

```bash
docker build -t formalda:local .
```

## Диагностика

Состояние сервисов и журналы:

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f mariadb
```

Если порт занят, измените соответствующее значение в `.env`. Например, `APP_PORT=8081` откроет форум по адресу <http://localhost:8081>; в этом случае также укажите `FORMALDA_BASE_URL=http://localhost:8081`.
