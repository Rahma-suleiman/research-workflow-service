# Workflow Engine — Credentials and Configuration Reference

## Project
- Project: workflow-engine
- Java: 21
- Spring Boot: 4.1.1
- Package: com.zanzibar.workflow
- Local service port: 8081
- Executable JAR:
  target/workflow-engine-0.0.1-SNAPSHOT.jar

## PostgreSQL
- Main database: workflow_engine_db
- Test database: workflow_engine_test_db
- Database host: localhost
- PostgreSQL port: 5432
- Database username: workflow_service_user
- Database password variable: WORKFLOW_DB_PASSWORD
- Test profile: src/test/resources/application-test.properties

## Security credentials
- Administrator username variable: WORKFLOW_ADMIN_USERNAME
- Administrator password variable: WORKFLOW_ADMIN_PASSWORD
- Service username variable: WORKFLOW_SERVICE_USERNAME
- Service password variable: WORKFLOW_SERVICE_PASSWORD
- Actor HMAC signing secret variable: WORKFLOW_ACTOR_SIGNING_SECRET

Actual production values are stored separately in the approved secrets store.
Never commit passwords, signing secrets, tokens, or private keys to Git.

## Test-only accounts
- Administrator username: workflow_test_admin
- Service username: workflow_test_service
- Test passwords are defined in application-test.properties.
- These accounts and passwords are for automated tests only.

## Important configuration files
- src/main/resources/application.properties
- src/main/resources/application-prod.properties (if present)
- src/test/resources/application-test.properties
- src/main/java/com/zanzibar/workflow/config/SecurityConfig.java
- src/main/java/com/zanzibar/workflow/service/WorkflowActorSignatureService.java

## Build and test
- Run full verification: ./mvnw clean verify
- Run tests: ./mvnw test
- Check build artifact:
  ls -lh target/workflow-engine-0.0.1-SNAPSHOT.jar

## Operational notes
- Keep the workflow database separate from the databases of the projects that call it.
- The backend must authenticate to this service and sign actor assertions.
- Keep production credentials out of source control.
- Rotate any real credentials that have been exposed.
- Update this note when configuration names or storage locations change.
