package com.example.uhf.db;

/**
 * Unified display item for the borrow/return ExpandableListView.
 * Used for both group items (boxes, standalone items) and child items (contents).
 */
public class DisplayItem {
    // Type constants
    public static final int TYPE_BOX = 0;
    public static final int TYPE_CONTENT = 1;
    public static final int TYPE_STANDALONE = 2;

    public String epc = "";
    public String name = "";
    public int type = TYPE_BOX;

    // For content items: which box they belong to
    public String boxEpc = "";
    public String boxName = "";

    // Audit status (has this item been scanned during current session?)
    public boolean audited = false;

    // Borrow status from database
    // "IN_STOCK" or "BORROWED"
    public String borrowStatus = "IN_STOCK";

    // For box items: counts
    public int totalContents = 0;
    public int auditedContents = 0;

    // Photo path (optional)
    public String photoPath = "";

    // Original DB id (for contents)
    public long dbId = 0;

    // Extended searchable fields (populated from StockInInfo)
    public String description = "";
    public String category = "";
    public String itemNumber = "";
    public String shelf = "";
    public String room = "";

    public DisplayItem() {}

    /**
     * Create a DisplayItem from a BoxInfo.
     */
    public static DisplayItem fromBox(BoxInfo box) {
        DisplayItem item = new DisplayItem();
        item.epc = box.epc;
        item.name = box.shortId != null && !box.shortId.isEmpty() ? box.shortId : box.epc;
        item.type = TYPE_BOX;
        item.photoPath = box.photoPath != null ? box.photoPath : "";
        return item;
    }

    /**
     * Create a DisplayItem from a ContentInfo.
     */
    public static DisplayItem fromContent(ContentInfo content) {
        DisplayItem item = new DisplayItem();
        item.epc = content.epc;
        item.name = content.shortId != null && !content.shortId.isEmpty() ? content.shortId : content.epc;
        item.type = TYPE_CONTENT;
        item.boxEpc = content.boxEpc != null ? content.boxEpc : "";
        item.photoPath = content.photoPath != null ? content.photoPath : "";
        item.dbId = content.id;
        return item;
    }

    /**
     * Create a DisplayItem from a StockInInfo (standalone).
     */
    public static DisplayItem fromStandalone(StockInInfo stockIn) {
        DisplayItem item = new DisplayItem();
        item.epc = stockIn.epc;
        item.name = stockIn.shortId != null && !stockIn.shortId.isEmpty() ? stockIn.shortId : stockIn.epc;
        item.type = TYPE_STANDALONE;
        item.photoPath = (stockIn.photoPaths != null && !stockIn.photoPaths.isEmpty()) ? stockIn.photoPaths.get(0) : "";
        return item;
    }

    /**
     * Get display name for the item.
     */
    public String getDisplayName() {
        return name;
    }

    /**
     * Check if this item is a group item (box or standalone).
     */
    public boolean isGroup() {
        return type == TYPE_BOX || type == TYPE_STANDALONE;
    }

    @Override
    public String toString() {
        return name;
    }
}
