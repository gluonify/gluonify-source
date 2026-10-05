// À exécuter UNE FOIS par un administrateur de Gdown (POST /db/neo4j/query, un énoncé à la fois) pour que l'application ait SA base et SON compte, confinés.
CREATE DATABASE source IF NOT EXISTS;
CREATE ROLE source_rw IF NOT EXISTS;
GRANT ACCESS ON DATABASE source TO source_rw;
GRANT MATCH {*} ON GRAPH source TO source_rw;
GRANT WRITE ON GRAPH source TO source_rw;
GRANT INDEX MANAGEMENT ON DATABASE source TO source_rw;
CREATE USER notes IF NOT EXISTS SET PASSWORD 'REMPLACEZ-PAR-UN-MOT-DE-PASSE-LONG';
GRANT ROLE source_rw TO notes;
