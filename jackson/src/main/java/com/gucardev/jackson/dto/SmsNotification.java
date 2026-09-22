package com.gucardev.jackson.dto;

public record SmsNotification(String phoneNumber, String message) implements Notification {}
