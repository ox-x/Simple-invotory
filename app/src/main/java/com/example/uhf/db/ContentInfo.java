package com.example.uhf.db;

/**
 * Model class for a Content item (Child) in the kitting system.
 */
public class ContentInfo {
    public long id;
    public String boxEpc;
    public String epc;
    public String shortId;
    public String description;
    public String photoPath;
    public String createdAt;

    /**
     * Transient field: used during checkout audit to track if item was scanned.
     */
    public transient boolean found = false;

    @Override
    public String toString() {
        String display = "";
        if (shortId != null && !shortId.isEmpty()) {
            display = shortId;
        } else if (description != null && !description.isEmpty()) {
            display = description;
        } else {
            display = epc;
        }
        return display;
    }
}
