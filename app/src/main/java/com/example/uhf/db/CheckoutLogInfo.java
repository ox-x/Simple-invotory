package com.example.uhf.db;

/**
 * Model class for a Checkout log entry.
 */
public class CheckoutLogInfo {
    public long id;
    public String studentName;
    public String studentId;
    public String boxEpc;
    public String boxShortId;
    public String status; // COMPLETE or MISSING
    public String missingItems;
    public String checkedItems;
    public String timestamp;
    public String itemType;       // BOX, CONTENT, STANDALONE
    public String parentBoxName;  // parent box name for CONTENT type

    public long getTimestampLong() {
        try {
            return Long.parseLong(timestamp);
        } catch (Exception e) {
            return 0;
        }
    }
}
