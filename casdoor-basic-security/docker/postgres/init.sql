-- Casdoor creates its own tables in "casdoor" (it connects as the postgres superuser).
CREATE DATABASE casdoor;

-- The Spring Boot application's database, separate from Casdoor's.
CREATE USER app WITH PASSWORD 'app';
CREATE DATABASE app OWNER app;
