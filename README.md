# trellis-app

A runnable Spring Boot service built on the [Trellis](https://github.com/akshaysethi12/trellis) workflow engine.
Trellis itself is a library published to GitHub Packages; this app depends on it like any other service would.

It provides every host bean Trellis needs (task context, hook strategies, Mongo converters, transactions,
dependency actions), a mobile-SDK-compatible execution API, a sample workflow, example extensions and mock
external services, so you can try the whole engine end to end.

## Prerequisites

- JDK 21 (`java -version`)
- Docker Desktop (for MongoDB)
- A GitHub personal access token (classic) with the `read:packages` scope, to download Trellis from GitHub Packages
- Postman (optional, for the API collection)

Maven is not needed; `./mvnw` downloads it.

## One-time setup: let Maven download Trellis

GitHub Packages needs authentication even for downloads. Add this to `~/.m2/settings.xml` (create the file if it
doesn't exist; [`settings.example.xml`](settings.example.xml) has the full file):

```xml
<settings>
    <servers>
        <server>
            <id>github-trellis</id>
            <username>YOUR_GITHUB_USERNAME</username>
            <password>YOUR_TOKEN_WITH_read:packages</password>
        </server>
    </servers>
</settings>
```

The `id` must match the `<repository>` id in `pom.xml`.

**CI** needs the same kind of token: in this repository go to *Settings → Secrets and variables → Actions* and add a
repository secret named `TRELLIS_PACKAGES_TOKEN` containing a classic token with `read:packages`. The built-in
`GITHUB_TOKEN` can't be used because Maven packages on GitHub can only be read by their own repository's workflows.

## Start

```bash
docker compose up -d                 # MongoDB 7 as replica set rs0 on :27017, mongo-express on :8081
docker compose ps                    # wait until trellis-mongo shows "healthy"
./mvnw spring-boot:run               # downloads Trellis, starts on http://localhost:8080
```

On startup the seed loader creates 8 task types and publishes the `SAMPLE_ONBOARDING` workflow (17 tasks) from
`src/main/resources/trellis-seed/sample-onboarding.json`.

| URL | What |
|---|---|
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/swagger-ui.html | Every endpoint, with "Try it out" |
| http://localhost:8081 | mongo-express: browse the `trellis` database |
| http://localhost:8080/mock/webhooks | Webhooks the sample workflow sent |

MongoDB must be a replica set because publishing a workflow runs in a transaction. To use a standalone `mongod`
instead, remove `?replicaSet=rs0` from `spring.data.mongodb.uri` and set `trellis.mongo.transactions-enabled: false`.

## Trellis version

The Trellis version is the `trellis.version` property in `pom.xml`. To upgrade, change it and rebuild.

To try an unreleased Trellis change: in the trellis repository run `./mvnw install`, then here run
`./mvnw spring-boot:run -Dtrellis.version=0.2.0-SNAPSHOT` (use the version from trellis's `pom.xml`).
Maven uses the copy in `~/.m2` before going to GitHub Packages.

## Test with Postman

Import both files from `postman/`:

- `Trellis.postman_collection.json` (69 requests with test scripts)
- `Trellis-local.postman_environment.json` (sets `baseUrl`)

Run the folders top to bottom, one request at a time or with the Collection Runner. Earlier requests save IDs
(`workflowId`, `draftWorkflowId`, `assetId`) into collection variables for later ones.

| Folder | What it shows |
|---|---|
| 1. Health | The app is up |
| 2. Task types | Task types and their JSON schemas |
| 3. Workflows (read) | The seeded workflow, its tree, versions and export |
| 4. Build a workflow | Create a draft, save a task tree, add, attach, update, clone and delete tasks, publish (with and without the publisher role), edit, diff, publish v2, copy, import |
| 5. Execute a workflow | The runtime API a mobile client uses: validation failures (Java validator, bean validation hook, HTTP validation hook), dependencies that show and hide tasks, a side effect, a sync HTTP webhook and an async bean hook, early submit, submit, edit-after-submit rejection, reset and the archived snapshot |
| 6. Media | Local stand-in for signed-URL uploads |
| 7. Mock services | The webhook receiver and fake validation API that the sample hooks call |
| 8. Utility | Mark tasks as system input, copy tasks between workflows |

Start a fresh execution with "5. Execute a workflow / Start a new asset", which generates a new `assetId`.

## What the sample workflow exercises

| Task | Feature |
|---|---|
| `full_name` | `beanValidationHook` `minLengthValidator` (min 3 chars), `dependencyBean` `answerSummary` |
| `email` | `httpValidationHook` to `/mock/validate` (rejects values containing "invalid") |
| `customer_type` | `TASK_VISIBILITY_ACTION`: "Business" shows `company_name` |
| `contacts_count` | `NUMERIC_TASK_ACTIVATION_ACTION`: activates `contact_1_name` / `contact_2_name` |
| `id_photo` | Side effect bean `logSideEffect` |
| `terms` | Sync `httpHook` to `/mock/webhooks/terms-accepted` when completed, async `beanHook` `echoHook` |
| every task | `NotEmptyAnswerValidator` (Java `AbstractValidator`) |

The example beans are in `src/main/java/io/trellis/app/examples`.

## MongoDB collections

| Collection | Contents |
|---|---|
| `task_types` | Task type definitions and their meta JSON schema |
| `workflows` | One document per workflow version (`workflowCode`, `workflowVersion`, `workflowStatus`) |
| `workflow_tasks` | Task definitions, one document per task per workflow version |
| `graph_info` | Designer layout (nodes and edges) |
| `captured_task_data` | One document per asset execution, answers keyed by task id |
| `captured_task_data_auditing` | Change history of `captured_task_data` |
| `mock_webhooks` | Webhooks received by the mock receiver (local testing only) |

## Configuration (`src/main/resources/application.yml`)

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `spring.data.mongodb.uri` | `mongodb://localhost:27017/trellis?replicaSet=rs0&directConnection=true` | Mongo connection |
| `trellis.mongo.transactions-enabled` | `true` | `false` for a standalone mongod |
| `trellis.runtime.default-tenant` | `DEFAULT` | Used when no `tenantId` header is sent |
| `trellis.runtime.seed.enabled` | `true` | Load `trellis-seed/*.json` on startup |
| `trellis.runtime.media.local-dir` | `./data/media` | Where uploaded files are stored (`TRELLIS_MEDIA_DIR`) |
| `trellis.app.mock-base-url` | `http://localhost:8080` | Base URL the sample hooks call |
| `trellis.storage.providers.s3.*` | dummy values | Required by Trellis's S3 beans; only used for real S3 uploads |

## Troubleshooting

- **`401 Unauthorized` or `Could not find artifact io.trellis:...`**: the GitHub token is missing, lacks `read:packages`, or the `<server>` id isn't `github-trellis`. Also check that the `trellis.version` has been published (the trellis repository's Packages page lists versions).

- **`MongoTimeoutException` / "No server chosen"**: the replica set is not initiated yet. Wait for `docker compose ps` to show healthy.
- **Port 27017 or 8080 in use**: stop the other MongoDB or app, or change the port mapping and `spring.data.mongodb.uri`.
- **Publish returns 403**: send header `x-user-roles: TRELLIS-WORKFLOW-PUBLISHER`.
- **SDK calls return 409 "not published"**: publish the workflow first; clients can only run published workflows.
