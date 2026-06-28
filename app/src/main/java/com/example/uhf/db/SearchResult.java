package com.example.uhf.db;

/**
 * Model class for search results (fuzzy search across boxes and contents).
 */
public class SearchResult {
    public String type; // "BOX" or "CONTENT"
    public String epc;
    public String shortId;
    public String description;
    public String photoPath;
    public String boxEpc; // Only for CONTENT type

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type).append("] ");
        if (shortId != null && !shortId.isEmpty()) {
            sb.append(shortId);
        }
        if (description != null && !description.isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(description);
        }
        if (epc != null && !epc.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("TID: ").append(epc);
        }
        return sb.toString();
    }
}
