package com.gucardev.jackson.dto;

public record EmailNotification(String to, String subject) implements Notification {}
