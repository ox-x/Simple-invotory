package com.example.uhf.db;

/**
 * Model class for a Box (Parent) in the kitting system.
 */
public class BoxInfo {
    public long id;
    public String epc;
    public String shortId;
    public String description;
    public String photoPath;
    public String createdAt;

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
