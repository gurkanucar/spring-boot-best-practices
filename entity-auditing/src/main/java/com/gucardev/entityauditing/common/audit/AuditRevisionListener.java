package com.gucardev.entityauditing.common.audit;

import org.hibernate.envers.RevisionListener;

/** Called by Envers once per new revision, inside the same transaction as the change. */
public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((AuditRevision) revisionEntity).setUsername(CurrentUser.get());
    }
}
