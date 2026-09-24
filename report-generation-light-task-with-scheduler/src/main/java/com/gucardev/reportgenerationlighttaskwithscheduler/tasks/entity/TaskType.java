package com.gucardev.reportgenerationlighttaskwithscheduler.tasks.entity;

import com.gucardev.reportgenerationlighttaskwithscheduler.tasks.handler.TaskHandler;

/** Every type needs exactly one {@link TaskHandler} bean. */
public enum TaskType {
    REPORT_GENERATION,
    EMAIL_SEND,
    REPORT_SHARE
}
