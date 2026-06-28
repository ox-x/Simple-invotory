package com.example.uhf.db;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class for a Stock-In record (入库记录).
 */
public class StockInInfo {
    public long id;
    public String epc;
    public String shortId;
    public String description;
    public String category;     // 种类
    public String itemNumber;   // 货号
    public String shelf;        // 货架
    public String room;         // 房间
    public List<String> photoPaths = new ArrayList<>(); // 多张照片路径
    public String boxEpc;       // If added to a box, otherwise empty
    public String type;         // BOX, CONTENT, STANDALONE
    public String timestamp;

    /**
     * Convert photoPaths list to JSON string for database storage.
     */
    public String photoPathsToJson() {
        if (photoPaths == null || photoPaths.isEmpty()) {
            return "[]";
        }
        JSONArray arr = new JSONArray();
        for (String p : photoPaths) {
            arr.put(p);
        }
        return arr.toString();
    }

    /**
     * Parse JSON string to photoPaths list.
     */
    public static List<String> jsonToPhotoPaths(String json) {
        List<String> list = new ArrayList<>();
        if (json == null || json.isEmpty()) return list;
        // Support legacy single-path (not starting with '[')
        if (!json.startsWith("[")) {
            list.add(json);
            return list;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String p = arr.optString(i, "");
                if (!p.isEmpty()) {
                    list.add(p);
                }
            }
        } catch (JSONException e) {
            // Fallback: treat as single path
            list.add(json);
        }
        return list;
    }

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
        return display + " [" + type + "]";
    }
}
