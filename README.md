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

- JUnit 5 / Mockito
- Testcontainers
- OpenAPI / Swagger UI
- GitHub Actions

## Архитектура

Проект использует подход `package-by-feature`.

```text
src/main/java/org/example/teamflowbackend
├── comment
│   └── dto
├── common
│   └── exception
├── project
│   └── dto
├── security
├── task
│   └── dto
└── user
    └── dto
```

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

## Запуск проекта

### Требования

Необходимо установить:

- JDK;
- PostgreSQL.

В проекте используется Gradle Wrapper, поэтому отдельно устанавливать Gradle не требуется.

Создайте PostgreSQL database и настройте datasource.

Пример локальной конфигурации:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/teamflowdb
spring.datasource.username=teamflow
spring.datasource.password=<local-password>

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
```

Flyway автоматически создаст и проверит необходимую структуру БД при старте приложения.

## Текущая аутентификация

Интеграция с Keycloak находится в разработке.

На текущем этапе используется временная development identity для проверки бизнес-логики проектов, задач и комментариев.

После подключения Spring Security идентификатор пользователя будет извлекаться из JWT claim `sub`, полученного от Keycloak.

Production-вариант не будет использовать локально захардкоженного пользователя.

## Цель проекта

Основная цель TeamFlow — практика production-oriented Java backend development:

Проект развивается поэтапно, при этом каждый новый инфраструктурный компонент добавляется после завершения и тестирования основной бизнес-логики.