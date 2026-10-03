# Deployment

The deployment artifact is `target/ticketscenter.war`; `Dockerfile` packages that same WAR under a non-root `tomcat` user. Supply configuration only through runtime environment variables. Never use build args for database, Redis, mail, VNPAY, or admin secrets.

```bash
docker build -t ticketscenter:local .
docker run --rm -p 8080:8080 --env-file .env.runtime ticketscenter:local
curl -f http://localhost:8080/ticketscenter/health/live
curl -f http://localhost:8080/ticketscenter/health/ready
```

`/health/live` does no dependency I/O. `/health/ready` performs the bounded persistence readiness check and must be used for traffic admission. Azure deployment remains separate from local verification: apply migrations with the migration identity, configure HTTPS and runtime principals, then run readiness against that environment.

The local Docker daemon was unavailable during Day 20, so image build/container evidence is blocked rather than inferred from the Dockerfile.
