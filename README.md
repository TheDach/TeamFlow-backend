# TeamFlow Backend

Backend-приложение для управления командными проектами и задачами.

TeamFlow представляет собой REST API для создания проектов, управления участниками, задачами и комментариями. Проект разрабатывается как modular monolith на Spring Boot и постепенно дополняется аутентификацией через Keycloak, тестированием, контейнеризацией и CI/CD.

Проект создаётся как pet-проект для практики backend-разработки на Java и приближен к типичному внутреннему task-management сервису.

## Возможности

На текущем этапе реализованы:

- управление пользователями;
- создание и просмотр проектов;
- редактирование, архивирование и восстановление проектов;
- роли участников проекта `OWNER` и `MEMBER`;
- добавление и удаление участников;
- автоматическое снятие назначенных задач при удалении участника;
- создание и редактирование задач;
- статусы задач и контролируемые переходы между ними;
- приоритеты задач;
- назначение исполнителей;
- фильтрация, сортировка и пагинация задач;
- комментарии к задачам;
- проверка автора комментария и владельца проекта при удалении;
- ограничения на изменение архивных проектов;
- единая обработка основных ошибок через `ProblemDetail`;
- аутентификация через Keycloak по OAuth 2.0 / OpenID Connect;
- защита REST API через Spring Security OAuth2 Resource Server;
- проверка JWT access token, выданных Keycloak;
- идентификация текущего пользователя по JWT claim `sub`;
- автоматическая привязка существующего пользователя или создание локального `User` при первом запросе;
- хранение статуса пользователя `ACTIVE` / `BLOCKED` отдельно от Identity Provider;
- управление схемой PostgreSQL через Flyway;
- индексы и ограничения целостности на уровне БД.

## Стек

**Backend**

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- Jakarta Bean Validation
- Spring Security
- OAuth 2.0 / OpenID Connect
- Keycloak
- Docker / Docker Compose

**Database**

- PostgreSQL
- Flyway
- SQL

**Build**

- Gradle Kotlin DSL
- Gradle Wrapper

**В разработке**

- преобразование Keycloak realm roles `USER` / `ADMIN` в Spring Security authorities;
- административный API для блокировки и разблокировки пользователей;
- JUnit 5 / Mockito;
- Testcontainers;
- OpenAPI / Swagger UI;
- GitHub Actions.

## Архитектура

Проект использует подход `package-by-feature`.

Основное разделение ответственности:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
PostgreSQL
```

`Controller` отвечает за HTTP-контракт и валидацию входных данных.

`Service` содержит бизнес-логику, проверки доступа и транзакционные операции.

`Repository` отвечает за работу с данными через Spring Data JPA.

Для HTTP API используются отдельные DTO — JPA entities напрямую клиенту не возвращаются.

## Модель данных

Основные сущности:

```text
User
 └── ProjectMember ── Project
                       │
                       └── Task
                            │
                            └── Comment
```

### User

Локальный профиль пользователя TeamFlow.

Keycloak отвечает за аутентификацию пользователя и хранение его credentials. TeamFlow не хранит пользовательские пароли. Связь между внешней учётной записью и локальным профилем выполняется через JWT claim `sub`, который сохраняется в `users.keycloak_id`.

Основные поля:

```text
id
keycloakId
email
displayName
status
createdAt
updatedAt
```

Статусы:

```text
ACTIVE
BLOCKED
```

### Project

Проект команды.

Статусы:

```text
ACTIVE
ARCHIVED
```

### ProjectMember

Связывает пользователя с проектом.

Роли:

```text
OWNER
MEMBER
```

### Task

Задача проекта.

Статусы:

```text
TODO
IN_PROGRESS
IN_REVIEW
DONE
```

Приоритеты:

```text
LOW
MEDIUM
HIGH
CRITICAL
```

### Comment

Комментарий пользователя к задаче.

## Переходы статусов задач

TeamFlow ограничивает допустимые переходы между состояниями задачи.

```text
TODO
 └── IN_PROGRESS

IN_PROGRESS
 ├── TODO
 └── IN_REVIEW

IN_REVIEW
 ├── IN_PROGRESS
 └── DONE

DONE
 └── TODO
```

Повторная установка текущего статуса является идемпотентной.

Недопустимый переход приводит к `409 Conflict`.

## API

Базовый путь:

```text
/api/v1
```

### Users

| Method | Endpoint | Description |
|---|---|---|
| GET | `/users/me` | Получить текущего пользователя |
| PATCH | `/users/me` | Изменить профиль |
| GET | `/users` | Поиск активных пользователей |
| GET | `/users/{id}` | Получить публичный профиль |

### Projects

| Method | Endpoint | Description |
|---|---|---|
| POST | `/projects` | Создать проект |
| GET | `/projects` | Получить проекты пользователя |
| GET | `/projects/{id}` | Получить проект |
| PATCH | `/projects/{id}` | Изменить проект |
| PATCH | `/projects/{id}/archive` | Архивировать проект |
| PATCH | `/projects/{id}/restore` | Восстановить проект |

### Project members

| Method | Endpoint | Description |
|---|---|---|
| GET | `/projects/{id}/members` | Получить участников |
| POST | `/projects/{id}/members` | Добавить участника |
| DELETE | `/projects/{id}/members/{userId}` | Удалить участника |

Добавлять и удалять участников может только `OWNER`.

При удалении участника задачи, назначенные на него, автоматически становятся неназначенными.

### Tasks

| Method | Endpoint | Description |
|---|---|---|
| POST | `/projects/{id}/tasks` | Создать задачу |
| GET | `/projects/{id}/tasks` | Получить задачи проекта |
| GET | `/tasks/{id}` | Получить задачу |
| PATCH | `/tasks/{id}` | Изменить задачу |
| PATCH | `/tasks/{id}/status` | Изменить статус |
| PATCH | `/tasks/{id}/assignee` | Изменить исполнителя |
| DELETE | `/tasks/{id}` | Удалить задачу |

Удалять задачи может только `OWNER`.

Для списка задач поддерживаются фильтры:

```text
status
priority
assigneeId
unassigned
dueBefore
page
size
sort
```

Пример:

```http
GET /api/v1/projects/{projectId}/tasks?status=TODO&priority=HIGH&size=20&sort=createdAt,desc
```

### Comments

| Method | Endpoint | Description |
|---|---|---|
| POST | `/tasks/{id}/comments` | Добавить комментарий |
| GET | `/tasks/{id}/comments` | Получить комментарии |
| DELETE | `/comments/{id}` | Удалить комментарий |

Комментарий может удалить его автор или `OWNER` проекта.

## Работа с архивными проектами

После перевода проекта в состояние `ARCHIVED` он остаётся доступным для чтения.

Изменение:

- проекта;
- состава участников;
- задач;
- комментариев

в архивном проекте запрещено.

## База данных

Схема БД управляется через Flyway.

Миграции находятся в:

```text
src/main/resources/db/migration
```

Hibernate используется в режиме проверки схемы:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Для часто используемых запросов созданы индексы, в том числе:

```text
project_members(user_id)
tasks(project_id, status)
tasks(project_id, assignee_id)
tasks(project_id, due_date)
comments(task_id, created_at)
```

## Аутентификация и безопасность

Для аутентификации используется Keycloak. Backend TeamFlow работает как OAuth 2.0 Resource Server и не хранит пароли пользователей.

Общая схема:

```text
Пользователь
    ↓
Keycloak
    ↓
OAuth 2.0 / OpenID Connect
    ↓
JWT access token
    ↓
Spring Security Resource Server
    ↓
SecurityContext
    ↓
LocalUserSyncFilter
    ↓
users.keycloak_id = JWT.sub
    ↓
Controller → Service → Repository
```

Spring Security получает настройки Identity Provider через `issuer-uri`, проверяет JWT и создаёт `Authentication`. Текущий пользователь определяется по claim `sub`.

`CurrentUser` получает `Jwt` из `SecurityContextHolder`, поэтому бизнес-модули `project`, `task` и `comment` не зависят от Keycloak API напрямую.

При первом аутентифицированном запросе TeamFlow:

1. получает `sub`, `email` и имя из проверенного JWT;
2. ищет пользователя по `users.keycloak_id`;
4. если пользователя нет, создаёт локальный профиль;
5. при последующих запросах использует `sub` как основной внешний идентификатор.

### Локальная конфигурация Keycloak

Для разработки используется отдельный realm:

```text
teamflow
```

Для тестирования Authorization Code Flow через Postman используется public OIDC client:

```text
teamflow-postman
```

Основные параметры:

```text
Standard Flow: ON
Client authentication: OFF
Direct Access Grants: OFF
Redirect URI: https://oauth.pstmn.io/v1/callback
```

Issuer локального realm:

```text
http://localhost:8180/realms/teamflow
```

OIDC discovery document:

```text
http://localhost:8180/realms/teamflow/.well-known/openid-configuration
```

## Цель проекта

Основная цель TeamFlow — практика production-oriented Java backend development:

Проект развивается поэтапно, при этом каждый новый инфраструктурный компонент добавляется после завершения и тестирования основной бизнес-логики.