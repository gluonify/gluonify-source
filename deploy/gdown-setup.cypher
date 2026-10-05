// Run ONCE by a Gdown administrator (POST /db/neo4j/query, one statement at a time) so that the application gets ITS OWN confined database and account.
CREATE DATABASE source IF NOT EXISTS;
CREATE ROLE source_rw IF NOT EXISTS;
GRANT ACCESS ON DATABASE source TO source_rw;
GRANT MATCH {*} ON GRAPH source TO source_rw;
GRANT WRITE ON GRAPH source TO source_rw;
GRANT INDEX MANAGEMENT ON DATABASE source TO source_rw;
CREATE USER notes IF NOT EXISTS SET PASSWORD 'REPLACE-WITH-A-LONG-PASSWORD';
GRANT ROLE source_rw TO notes;
