package com.murderhecoded.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @deprecated Superseded by {@link EditorService}. Retained for backward compatibility.
 */
@Deprecated
@Service
public class NarrativeEditorService extends EditorService {

    public NarrativeEditorService() {
        super(new EditorialScorer());
    }

    @Autowired
    public NarrativeEditorService(EditorialScorer scorer) {
        super(scorer);
    }
}
