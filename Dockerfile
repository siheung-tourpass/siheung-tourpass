# Local development only. docker-compose.yml builds the `db` and `app` targets.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY backend ./backend
COPY frontend ./frontend
RUN mvn -B -ntp -f backend/pom.xml package

FROM python:3.12-slim AS tourism-sql
WORKDIR /src
COPY backend/scripts ./backend/scripts
COPY backend/data/siheung-tourism.json backend/data/siheung-dining.json backend/data/siheung-tourpass.json ./backend/data/
# Without --replace-demo the demo places stay active for recommendations; tour pass merchants use real cards.
RUN python3 backend/scripts/tourism-sql.py > /tourism.sql && python3 backend/scripts/tourpass-sql.py > /tourpass.sql

# The init scripts run only when the database volume is empty.
FROM postgres:16 AS db
COPY backend/sql/schema.sql /docker-entrypoint-initdb.d/01-schema.sql
COPY backend/sql/seed.sql /docker-entrypoint-initdb.d/02-seed.sql
COPY --from=tourism-sql /tourism.sql /docker-entrypoint-initdb.d/03-tourism.sql
COPY --from=tourism-sql /tourpass.sql /docker-entrypoint-initdb.d/04-tourpass.sql
COPY backend/sql/remove-demo.sql /docker-entrypoint-initdb.d/05-remove-demo.sql

FROM eclipse-temurin:17-jre AS app
WORKDIR /app
COPY --from=build /src/backend/target/siheung-tourpass.war ./siheung-tourpass.war
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "siheung-tourpass.war"]
