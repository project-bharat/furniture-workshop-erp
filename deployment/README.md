# Local development infrastructure

The Docker Compose file starts a local PostgreSQL 16 instance for development. It binds to localhost only.

1. Set POSTGRES_PASSWORD in your shell or a local untracked environment file.
2. Run docker compose -f deployment/docker-compose.yml up -d.
3. Check readiness with docker compose -f deployment/docker-compose.yml ps.

The initial SQL file is mounted into PostgreSQL's first-initialization directory. PostgreSQL runs these scripts only when the data volume is first created. To test a clean database, remove the local volume intentionally; this deletes local database contents.

This is a development convenience, not the production migration strategy. Production must use a versioned migration runner, managed secrets, private networking, backups and tested restore procedures.
