-- The Spring Boot application's database, separate from Logto's ("logto", created by Logto's seed).
CREATE USER app WITH PASSWORD 'app';
CREATE DATABASE app OWNER app;
