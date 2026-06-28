package com.example.uhf.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database helper for RFID Kitting, Checkout, and Asset management.
 */
@SuppressLint("Range")
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "uhf_kitting.db";
    private static final int DB_VERSION = 4;

    // Tables
    public static final String TABLE_BOXES = "boxes";
    public static final String TABLE_CONTENTS = "contents";
    public static final String TABLE_CHECKOUT_LOGS = "checkout_logs";
    public static final String TABLE_STOCK_INS = "stock_ins";

    // Boxes columns
    public static final String BOX_ID = "id";
    public static final String BOX_EPC = "epc";
    public static final String BOX_SHORT_ID = "short_id";
    public static final String BOX_DESCRIPTION = "description";
    public static final String BOX_PHOTO_PATH = "photo_path";
    public static final String BOX_CREATED_AT = "created_at";

    // Contents columns
    public static final String CONTENT_ID = "id";
    public static final String CONTENT_BOX_EPC = "box_epc";
    public static final String CONTENT_EPC = "epc";
    public static final String CONTENT_SHORT_ID = "short_id";
    public static final String CONTENT_DESCRIPTION = "description";
    public static final String CONTENT_PHOTO_PATH = "photo_path";
    public static final String CONTENT_CREATED_AT = "created_at";

    // Checkout logs columns
    public static final String LOG_ID = "id";
    public static final String LOG_STUDENT_NAME = "student_name";
    public static final String LOG_STUDENT_ID = "student_id";
    public static final String LOG_BOX_EPC = "box_epc";
    public static final String LOG_BOX_SHORT_ID = "box_short_id";
    public static final String LOG_STATUS = "status"; // COMPLETE or MISSING
    public static final String LOG_MISSING_ITEMS = "missing_items";
    public static final String LOG_CHECKED_ITEMS = "checked_items";
    public static final String LOG_TIMESTAMP = "timestamp";
    public static final String LOG_ITEM_TYPE = "item_type";
    public static final String LOG_PARENT_BOX = "parent_box_name";

    // Stock-Ins columns
    public static final String SI_ID = "id";
    public static final String SI_EPC = "epc";
    public static final String SI_SHORT_ID = "short_id";
    public static final String SI_DESCRIPTION = "description";
    public static final String SI_PHOTO_PATH = "photo_path";     // legacy column (kept for migration)
    public static final String SI_PHOTO_PATHS = "photo_paths";   // JSON array of paths
    public static final String SI_CATEGORY = "category";
    public static final String SI_ITEM_NUMBER = "item_number";
    public static final String SI_SHELF = "shelf";
    public static final String SI_ROOM = "room";
    public static final String SI_BOX_EPC = "box_epc";
    public static final String SI_TYPE = "type"; // BOX, CONTENT, STANDALONE
    public static final String SI_TIMESTAMP = "timestamp";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_BOXES + " (" +
                BOX_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                BOX_EPC + " TEXT UNIQUE NOT NULL, " +
                BOX_SHORT_ID + " TEXT DEFAULT '', " +
                BOX_DESCRIPTION + " TEXT DEFAULT '', " +
                BOX_PHOTO_PATH + " TEXT DEFAULT '', " +
                BOX_CREATED_AT + " TEXT NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_CONTENTS + " (" +
                CONTENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                CONTENT_BOX_EPC + " TEXT NOT NULL, " +
                CONTENT_EPC + " TEXT NOT NULL, " +
                CONTENT_SHORT_ID + " TEXT DEFAULT '', " +
                CONTENT_DESCRIPTION + " TEXT DEFAULT '', " +
                CONTENT_PHOTO_PATH + " TEXT DEFAULT '', " +
                CONTENT_CREATED_AT + " TEXT NOT NULL, " +
                "FOREIGN KEY(" + CONTENT_BOX_EPC + ") REFERENCES " + TABLE_BOXES + "(" + BOX_EPC + "))");

        db.execSQL("CREATE TABLE " + TABLE_CHECKOUT_LOGS + " (" +
                LOG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                LOG_STUDENT_NAME + " TEXT DEFAULT '', " +
                LOG_STUDENT_ID + " TEXT DEFAULT '', " +
                LOG_BOX_EPC + " TEXT NOT NULL, " +
                LOG_BOX_SHORT_ID + " TEXT DEFAULT '', " +
                LOG_STATUS + " TEXT DEFAULT 'COMPLETE', " +
                LOG_MISSING_ITEMS + " TEXT DEFAULT '', " +
                LOG_CHECKED_ITEMS + " TEXT DEFAULT '', " +
                LOG_TIMESTAMP + " TEXT NOT NULL, " +
                LOG_ITEM_TYPE + " TEXT DEFAULT '', " +
                LOG_PARENT_BOX + " TEXT DEFAULT '')");

        db.execSQL("CREATE TABLE " + TABLE_STOCK_INS + " (" +
                SI_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                SI_EPC + " TEXT NOT NULL, " +
                SI_SHORT_ID + " TEXT DEFAULT '', " +
                SI_DESCRIPTION + " TEXT DEFAULT '', " +
                SI_PHOTO_PATH + " TEXT DEFAULT '', " +
                SI_PHOTO_PATHS + " TEXT DEFAULT '[]', " +
                SI_CATEGORY + " TEXT DEFAULT '', " +
                SI_ITEM_NUMBER + " TEXT DEFAULT '', " +
                SI_SHELF + " TEXT DEFAULT '', " +
                SI_ROOM + " TEXT DEFAULT '', " +
                SI_BOX_EPC + " TEXT DEFAULT '', " +
                SI_TYPE + " TEXT DEFAULT 'STANDALONE', " +
                SI_TIMESTAMP + " TEXT NOT NULL)");

        // Indexes for faster lookups
        db.execSQL("CREATE INDEX idx_contents_box_epc ON " + TABLE_CONTENTS + "(" + CONTENT_BOX_EPC + ")");
        db.execSQL("CREATE INDEX idx_contents_epc ON " + TABLE_CONTENTS + "(" + CONTENT_EPC + ")");
        db.execSQL("CREATE INDEX idx_logs_timestamp ON " + TABLE_CHECKOUT_LOGS + "(" + LOG_TIMESTAMP + ")");
        db.execSQL("CREATE INDEX idx_boxes_epc ON " + TABLE_BOXES + "(" + BOX_EPC + ")");
        db.execSQL("CREATE INDEX idx_stockins_epc ON " + TABLE_STOCK_INS + "(" + SI_EPC + ")");
        db.execSQL("CREATE INDEX idx_stockins_timestamp ON " + TABLE_STOCK_INS + "(" + SI_TIMESTAMP + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_STOCK_INS + " (" +
                    SI_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    SI_EPC + " TEXT NOT NULL, " +
                    SI_SHORT_ID + " TEXT DEFAULT '', " +
                    SI_DESCRIPTION + " TEXT DEFAULT '', " +
                    SI_PHOTO_PATH + " TEXT DEFAULT '', " +
                    SI_BOX_EPC + " TEXT DEFAULT '', " +
                    SI_TYPE + " TEXT DEFAULT 'STANDALONE', " +
                    SI_TIMESTAMP + " TEXT NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stockins_epc ON " + TABLE_STOCK_INS + "(" + SI_EPC + ")");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stockins_timestamp ON " + TABLE_STOCK_INS + "(" + SI_TIMESTAMP + ")");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_LOGS + " ADD COLUMN " + LOG_ITEM_TYPE + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE_CHECKOUT_LOGS + " ADD COLUMN " + LOG_PARENT_BOX + " TEXT DEFAULT ''");
        }
        if (oldVersion < 4) {
            // Add new columns to stock_ins table
            db.execSQL("ALTER TABLE " + TABLE_STOCK_INS + " ADD COLUMN " + SI_PHOTO_PATHS + " TEXT DEFAULT '[]'");
            db.execSQL("ALTER TABLE " + TABLE_STOCK_INS + " ADD COLUMN " + SI_CATEGORY + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE_STOCK_INS + " ADD COLUMN " + SI_ITEM_NUMBER + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE_STOCK_INS + " ADD COLUMN " + SI_SHELF + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE_STOCK_INS + " ADD COLUMN " + SI_ROOM + " TEXT DEFAULT ''");
            // Migrate old photo_path data to photo_paths JSON format
            db.execSQL("UPDATE " + TABLE_STOCK_INS + " SET " + SI_PHOTO_PATHS + "='[\"' || " + SI_PHOTO_PATH + " || '\"]' WHERE " + SI_PHOTO_PATH + " != '' AND " + SI_PHOTO_PATH + " IS NOT NULL");
        }
    }

    // ==================== BOX Operations ====================

    public long insertBox(String epc, String shortId, String description, String photoPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BOX_EPC, epc);
        cv.put(BOX_SHORT_ID, shortId != null ? shortId : "");
        cv.put(BOX_DESCRIPTION, description != null ? description : "");
        cv.put(BOX_PHOTO_PATH, photoPath != null ? photoPath : "");
        cv.put(BOX_CREATED_AT, System.currentTimeMillis() + "");
        return db.insertWithOnConflict(TABLE_BOXES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public BoxInfo getBoxByEpc(String epc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_BOXES, null, BOX_EPC + "=?", new String[]{epc}, null, null, null);
        BoxInfo box = null;
        if (c.moveToFirst()) {
            box = cursorToBox(c);
        }
        c.close();
        return box;
    }

    public List<BoxInfo> getAllBoxes() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_BOXES, null, null, null, null, null, BOX_CREATED_AT + " DESC");
        List<BoxInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToBox(c));
        }
        c.close();
        return list;
    }

    public void deleteBox(String epc) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CONTENTS, CONTENT_BOX_EPC + "=?", new String[]{epc});
        db.delete(TABLE_BOXES, BOX_EPC + "=?", new String[]{epc});
    }

    /**
     * Update a box's short_id, description, and photo_path by EPC.
     */
    public int updateBox(String epc, String shortId, String description, String photoPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(BOX_SHORT_ID, shortId != null ? shortId : "");
        cv.put(BOX_DESCRIPTION, description != null ? description : "");
        cv.put(BOX_PHOTO_PATH, photoPath != null ? photoPath : "");
        return db.update(TABLE_BOXES, cv, BOX_EPC + "=?", new String[]{epc});
    }

    /**
     * Update a content record's short_id, description, and photo_path by box_epc + content epc.
     */
    public int updateContentByEpc(String boxEpc, String contentEpc, String shortId, String description, String photoPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(CONTENT_SHORT_ID, shortId != null ? shortId : "");
        cv.put(CONTENT_DESCRIPTION, description != null ? description : "");
        cv.put(CONTENT_PHOTO_PATH, photoPath != null ? photoPath : "");
        return db.update(TABLE_CONTENTS, cv,
                CONTENT_BOX_EPC + "=? AND " + CONTENT_EPC + "=?",
                new String[]{boxEpc, contentEpc});
    }

    /**
     * Delete a content record by box_epc + content epc.
     */
    public void deleteContentByEpc(String boxEpc, String contentEpc) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CONTENTS,
                CONTENT_BOX_EPC + "=? AND " + CONTENT_EPC + "=?",
                new String[]{boxEpc, contentEpc});
    }

    // ==================== CONTENT Operations ====================

    public long insertContent(String boxEpc, String contentEpc, String shortId, String description, String photoPath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(CONTENT_BOX_EPC, boxEpc);
        cv.put(CONTENT_EPC, contentEpc);
        cv.put(CONTENT_SHORT_ID, shortId != null ? shortId : "");
        cv.put(CONTENT_DESCRIPTION, description != null ? description : "");
        cv.put(CONTENT_PHOTO_PATH, photoPath != null ? photoPath : "");
        cv.put(CONTENT_CREATED_AT, System.currentTimeMillis() + "");
        return db.insert(TABLE_CONTENTS, null, cv);
    }

    public List<ContentInfo> getContentsByBoxEpc(String boxEpc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_CONTENTS, null, CONTENT_BOX_EPC + "=?",
                new String[]{boxEpc}, null, null, CONTENT_CREATED_AT + " ASC");
        List<ContentInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToContent(c));
        }
        c.close();
        return list;
    }

    public void deleteContent(long contentId) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CONTENTS, CONTENT_ID + "=?", new String[]{String.valueOf(contentId)});
    }

    public boolean contentExists(String boxEpc, String contentEpc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_CONTENTS, new String[]{CONTENT_ID},
                CONTENT_BOX_EPC + "=? AND " + CONTENT_EPC + "=?",
                new String[]{boxEpc, contentEpc}, null, null, null);
        boolean exists = c.getCount() > 0;
        c.close();
        return exists;
    }

    /**
     * Find which box a content item belongs to by its EPC.
     * Returns the BoxInfo if found, null otherwise.
     */
    @SuppressLint("Range")
    public BoxInfo getBoxForContent(String contentEpc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_CONTENTS, new String[]{CONTENT_BOX_EPC},
                CONTENT_EPC + "=?", new String[]{contentEpc}, null, null, null, "1");
        BoxInfo box = null;
        if (c.moveToFirst()) {
            String boxEpc = c.getString(c.getColumnIndex(CONTENT_BOX_EPC));
            box = getBoxByEpc(boxEpc);
        }
        c.close();
        return box;
    }

    // ==================== CHECKOUT LOG Operations ====================

    public long insertCheckoutLog(String studentId, String itemEpc, String itemName,
                                  String status, String itemType, String parentBoxName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(LOG_STUDENT_NAME, studentId != null ? studentId : "");
        cv.put(LOG_STUDENT_ID, studentId != null ? studentId : "");
        cv.put(LOG_BOX_EPC, itemEpc != null ? itemEpc : "");
        cv.put(LOG_BOX_SHORT_ID, itemName != null ? itemName : "");
        cv.put(LOG_STATUS, status);
        cv.put(LOG_MISSING_ITEMS, "");
        cv.put(LOG_CHECKED_ITEMS, "");
        cv.put(LOG_TIMESTAMP, System.currentTimeMillis() + "");
        cv.put(LOG_ITEM_TYPE, itemType != null ? itemType : "");
        cv.put(LOG_PARENT_BOX, parentBoxName != null ? parentBoxName : "");
        return db.insert(TABLE_CHECKOUT_LOGS, null, cv);
    }

    public List<CheckoutLogInfo> getAllCheckoutLogs() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_CHECKOUT_LOGS, null, null, null, null, null, LOG_TIMESTAMP + " DESC");
        List<CheckoutLogInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToLog(c));
        }
        c.close();
        return list;
    }

    public List<CheckoutLogInfo> getCheckoutLogsByDateRange(long startTime, long endTime) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_CHECKOUT_LOGS, null,
                LOG_TIMESTAMP + ">=? AND " + LOG_TIMESTAMP + "<=?",
                new String[]{String.valueOf(startTime), String.valueOf(endTime)},
                null, null, LOG_TIMESTAMP + " DESC");
        List<CheckoutLogInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToLog(c));
        }
        c.close();
        return list;
    }

    // ==================== Smart Search ====================

    /**
     * Fuzzy search across boxes and contents by EPC, short_id, or description.
     */
    @SuppressLint("Range")
    public List<SearchResult> fuzzySearch(String query) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }
        String search = "%" + query.trim() + "%";
        SQLiteDatabase db = getReadableDatabase();

        // Search boxes
        Cursor c = db.query(TABLE_BOXES, null,
                BOX_EPC + " LIKE ? OR " + BOX_SHORT_ID + " LIKE ? OR " + BOX_DESCRIPTION + " LIKE ?",
                new String[]{search, search, search}, null, null, BOX_CREATED_AT + " DESC", "20");
        while (c.moveToNext()) {
            SearchResult r = new SearchResult();
            r.type = "BOX";
            r.epc = c.getString(c.getColumnIndex(BOX_EPC));
            r.shortId = c.getString(c.getColumnIndex(BOX_SHORT_ID));
            r.description = c.getString(c.getColumnIndex(BOX_DESCRIPTION));
            r.photoPath = c.getString(c.getColumnIndex(BOX_PHOTO_PATH));
            results.add(r);
        }
        c.close();

        // Search contents
        c = db.query(TABLE_CONTENTS, null,
                CONTENT_EPC + " LIKE ? OR " + CONTENT_SHORT_ID + " LIKE ? OR " + CONTENT_DESCRIPTION + " LIKE ?",
                new String[]{search, search, search}, null, null, CONTENT_CREATED_AT + " DESC", "30");
        while (c.moveToNext()) {
            SearchResult r = new SearchResult();
            r.type = "CONTENT";
            r.epc = c.getString(c.getColumnIndex(CONTENT_EPC));
            r.shortId = c.getString(c.getColumnIndex(CONTENT_SHORT_ID));
            r.description = c.getString(c.getColumnIndex(CONTENT_DESCRIPTION));
            r.photoPath = c.getString(c.getColumnIndex(CONTENT_PHOTO_PATH));
            r.boxEpc = c.getString(c.getColumnIndex(CONTENT_BOX_EPC));
            results.add(r);
        }
        c.close();

        return results;
    }

    // ==================== STOCK-IN Operations ====================

    public long insertStockIn(String epc, String shortId, String description, String photoPathsJson,
                              String category, String itemNumber, String shelf, String room,
                              String boxEpc, String type) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(SI_EPC, epc);
        cv.put(SI_SHORT_ID, shortId != null ? shortId : "");
        cv.put(SI_DESCRIPTION, description != null ? description : "");
        cv.put(SI_PHOTO_PATH, ""); // legacy compat
        cv.put(SI_PHOTO_PATHS, photoPathsJson != null ? photoPathsJson : "[]");
        cv.put(SI_CATEGORY, category != null ? category : "");
        cv.put(SI_ITEM_NUMBER, itemNumber != null ? itemNumber : "");
        cv.put(SI_SHELF, shelf != null ? shelf : "");
        cv.put(SI_ROOM, room != null ? room : "");
        cv.put(SI_BOX_EPC, boxEpc != null ? boxEpc : "");
        cv.put(SI_TYPE, type != null ? type : "STANDALONE");
        cv.put(SI_TIMESTAMP, System.currentTimeMillis() + "");
        return db.insert(TABLE_STOCK_INS, null, cv);
    }

    public List<StockInInfo> getRecentStockIns(int limit) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_STOCK_INS, null, null, null, null, null,
                SI_TIMESTAMP + " DESC", String.valueOf(limit));
        List<StockInInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToStockIn(c));
        }
        c.close();
        return list;
    }

    public List<StockInInfo> getAllStockIns() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_STOCK_INS, null, null, null, null, null,
                SI_TIMESTAMP + " DESC");
        List<StockInInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToStockIn(c));
        }
        c.close();
        return list;
    }

    public int getStockInCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_STOCK_INS, null);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    public boolean stockInExists(String epc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_STOCK_INS, new String[]{SI_ID},
                SI_EPC + "=?", new String[]{epc}, null, null, null);
        boolean exists = c.getCount() > 0;
        c.close();
        return exists;
    }

    /**
     * Get a stock-in record by its primary key id.
     */
    public StockInInfo getStockInById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_STOCK_INS, null,
                SI_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null, "1");
        StockInInfo info = null;
        if (c.moveToFirst()) {
            info = cursorToStockIn(c);
        }
        c.close();
        return info;
    }

    /**
     * Delete a single stock-in record by id.
     * Cascades to boxes/contents tables based on type.
     */
    public void deleteStockIn(long id) {
        StockInInfo info = getStockInById(id);
        if (info == null) return;

        if ("BOX".equals(info.type)) {
            // Deleting a BOX: also delete the box and all its contents
            deleteBox(info.epc);
        } else if ("CONTENT".equals(info.type)) {
            // Deleting a CONTENT: also remove from the contents table
            if (info.boxEpc != null && !info.boxEpc.isEmpty()) {
                deleteContentByEpc(info.boxEpc, info.epc);
            }
        }
        // STANDALONE: no associated box/content records

        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_STOCK_INS, SI_ID + "=?", new String[]{String.valueOf(id)});

        // Also clean up checkout_logs for this EPC
        deleteCheckoutLogsByEpc(info.epc);
    }

    /**
     * Update an existing stock-in record.
     * Also cascades short_id/description/photo_path changes to boxes/contents tables.
     */
    public int updateStockIn(long id, String shortId, String description,
                             String category, String itemNumber, String shelf, String room,
                             String photoPathsJson) {
        StockInInfo info = getStockInById(id);
        if (info == null) return 0;

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(SI_SHORT_ID, shortId != null ? shortId : "");
        cv.put(SI_DESCRIPTION, description != null ? description : "");
        cv.put(SI_CATEGORY, category != null ? category : "");
        cv.put(SI_ITEM_NUMBER, itemNumber != null ? itemNumber : "");
        cv.put(SI_SHELF, shelf != null ? shelf : "");
        cv.put(SI_ROOM, room != null ? room : "");
        if (photoPathsJson != null) {
            cv.put(SI_PHOTO_PATHS, photoPathsJson);
            cv.put(SI_PHOTO_PATH, ""); // legacy compat
        }
        int result = db.update(TABLE_STOCK_INS, cv, SI_ID + "=?", new String[]{String.valueOf(id)});

        // Cascade to boxes/contents
        if (result > 0) {
            String firstPhoto = "";
            if (photoPathsJson != null) {
                List<String> paths = StockInInfo.jsonToPhotoPaths(photoPathsJson);
                if (!paths.isEmpty()) firstPhoto = paths.get(0);
            } else {
                // Keep existing photo from stock_in record
                if (info.photoPaths != null && !info.photoPaths.isEmpty()) {
                    firstPhoto = info.photoPaths.get(0);
                }
            }

            if ("BOX".equals(info.type)) {
                updateBox(info.epc, shortId, description, firstPhoto);
            } else if ("CONTENT".equals(info.type) && info.boxEpc != null && !info.boxEpc.isEmpty()) {
                updateContentByEpc(info.boxEpc, info.epc, shortId, description, firstPhoto);
            }
        }
        return result;
    }

    /**
     * Delete all stock-in records.
     * Cascades to boxes and contents tables.
     */
    public void deleteAllStockIns() {
        List<StockInInfo> allRecords = getAllStockIns();
        for (StockInInfo info : allRecords) {
            if ("BOX".equals(info.type)) {
                deleteBox(info.epc);
            } else if ("CONTENT".equals(info.type) && info.boxEpc != null && !info.boxEpc.isEmpty()) {
                deleteContentByEpc(info.boxEpc, info.epc);
            }
            // Also clean up checkout_logs for each EPC
            deleteCheckoutLogsByEpc(info.epc);
        }
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_STOCK_INS, null, null);
    }

    /**
     * Delete all stock-in records for a given EPC.
     */
    public void deleteStockInsByEpc(String epc) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_STOCK_INS, SI_EPC + "=?", new String[]{epc});
    }

    /**
     * Delete all checkout_logs that reference a given EPC.
     * Matches box_epc, checked_items (LIKE), and missing_items (LIKE).
     */
    public void deleteCheckoutLogsByEpc(String epc) {
        SQLiteDatabase db = getWritableDatabase();
        String search = "%" + epc + "%";
        db.delete(TABLE_CHECKOUT_LOGS,
                LOG_BOX_EPC + "=? OR " + LOG_CHECKED_ITEMS + " LIKE ? OR " + LOG_MISSING_ITEMS + " LIKE ?",
                new String[]{epc, search, search});
    }

    /**
     * Purge all data for a given EPC across all tables (stock_ins, boxes, contents, checkout_logs).
     * Used before re-registering an already-existing tag to avoid data conflicts.
     */
    public void purgeAllDataByEpc(String epc) {
        deleteStockInsByEpc(epc);
        deleteBox(epc);
        // Also remove this EPC from any box's contents
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CONTENTS, CONTENT_EPC + "=?", new String[]{epc});
        deleteCheckoutLogsByEpc(epc);
    }

    // ==================== Clear All Data ====================

    /**
     * Delete all data from all business tables (boxes, contents, checkout_logs, stock_ins).
     */
    public void clearAllData() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_CHECKOUT_LOGS, null, null);
        db.delete(TABLE_CONTENTS, null, null);
        db.delete(TABLE_BOXES, null, null);
        db.delete(TABLE_STOCK_INS, null, null);
    }

    // ==================== Warehouse Stats ====================

    public int getTotalItemCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_CONTENTS, null);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    public int getBoxContentCount(String boxEpc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_CONTENTS, new String[]{"COUNT(*)"},
                CONTENT_BOX_EPC + "=?", new String[]{boxEpc}, null, null, null);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    /**
     * Get the latest checkout log for a specific box.
     * Returns null if no checkout record exists.
     */
    public CheckoutLogInfo getLatestCheckoutLog(String boxEpc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_CHECKOUT_LOGS, null,
                LOG_BOX_EPC + "=?", new String[]{boxEpc},
                null, null, LOG_TIMESTAMP + " DESC", "1");
        CheckoutLogInfo log = null;
        if (c.moveToFirst()) {
            log = cursorToLog(c);
        }
        c.close();
        return log;
    }

    /**
     * Get the current borrow status for an item by EPC.
     * Checks the latest log that references this EPC.
     * Returns "IN_STOCK" or "BORROWED".
     */
    public String getItemBorrowStatus(String epc) {
        SQLiteDatabase db = getReadableDatabase();
        String search = "%" + epc + "%";
        Cursor c = db.query(TABLE_CHECKOUT_LOGS, null,
                LOG_BOX_EPC + " = ? OR " + LOG_CHECKED_ITEMS + " LIKE ? OR " + LOG_MISSING_ITEMS + " LIKE ?",
                new String[]{epc, search, search},
                null, null, LOG_TIMESTAMP + " DESC", "1");
        String status = "IN_STOCK";
        if (c.moveToFirst()) {
            String logStatus = c.getString(c.getColumnIndex(LOG_STATUS));
            if ("BORROW".equals(logStatus)) {
                status = "BORROWED";
            } else if ("RETURN".equals(logStatus)) {
                status = "IN_STOCK";
            }
        }
        c.close();
        return status;
    }

    /**
     * Get the latest stock-in record for a specific EPC.
     * Returns null if no stock-in record exists.
     */
    public StockInInfo getLatestStockIn(String epc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_STOCK_INS, null,
                SI_EPC + "=?", new String[]{epc},
                null, null, SI_TIMESTAMP + " DESC", "1");
        StockInInfo info = null;
        if (c.moveToFirst()) {
            info = cursorToStockIn(c);
        }
        c.close();
        return info;
    }

    /**
     * Determine the status of a content item within a box.
     * Matches against shortId, description, AND epc since checkout logs store toString().
     * Returns one of: "IN_STOCK", "CHECKED_OUT", "MISSING"
     */
    public String getContentStatus(String boxEpc, String contentEpc, String contentShortId, String contentDesc) {
        CheckoutLogInfo log = getLatestCheckoutLog(boxEpc);
        if (log == null) {
            return "IN_STOCK"; // No checkout record = in stock
        }
        // Build list of possible identifiers for this content item
        List<String> identifiers = new ArrayList<>();
        if (contentEpc != null) identifiers.add(contentEpc);
        if (contentShortId != null && !contentShortId.isEmpty()) identifiers.add(contentShortId);
        if (contentDesc != null && !contentDesc.isEmpty()) identifiers.add(contentDesc);

        // Check if this item is in the missing list
        if (log.missingItems != null && !log.missingItems.isEmpty()) {
            String[] missing = log.missingItems.split(",\\s*");
            for (String m : missing) {
                String trimmed = m.trim();
                for (String id : identifiers) {
                    if (trimmed.equalsIgnoreCase(id) || trimmed.contains(id) || id.contains(trimmed)) {
                        return "MISSING";
                    }
                }
            }
        }
        // Check if this item is in the checked list
        if (log.checkedItems != null && !log.checkedItems.isEmpty()) {
            String[] checked = log.checkedItems.split(",\\s*");
            for (String ch : checked) {
                String trimmed = ch.trim();
                for (String id : identifiers) {
                    if (trimmed.equalsIgnoreCase(id) || trimmed.contains(id) || id.contains(trimmed)) {
                        return "CHECKED_OUT";
                    }
                }
            }
        }
        return "IN_STOCK";
    }

    // ==================== Cursor helpers ====================

    /**
     * Get all stock-in records for a specific EPC.
     */
    public List<StockInInfo> getStockInHistory(String epc) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_STOCK_INS, null,
                SI_EPC + "=?", new String[]{epc},
                null, null, SI_TIMESTAMP + " DESC");
        List<StockInInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToStockIn(c));
        }
        c.close();
        return list;
    }

    /**
     * Get all checkout logs that mention a specific EPC (in checked or missing items).
     */
    @SuppressLint("Range")
    public List<CheckoutLogInfo> getCheckoutHistoryForItem(String epc) {
        SQLiteDatabase db = getReadableDatabase();
        String search = "%" + epc + "%";
        Cursor c = db.query(TABLE_CHECKOUT_LOGS, null,
                LOG_CHECKED_ITEMS + " LIKE ? OR " + LOG_MISSING_ITEMS + " LIKE ? OR " + LOG_BOX_EPC + " = ?",
                new String[]{search, search, epc},
                null, null, LOG_TIMESTAMP + " DESC");
        List<CheckoutLogInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToLog(c));
        }
        c.close();
        return list;
    }

    /**
     * Get all standalone stock-in items (type = STANDALONE).
     */
    public List<StockInInfo> getStandaloneItems() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_STOCK_INS, null,
                SI_TYPE + "=?", new String[]{"STANDALONE"},
                null, null, SI_TIMESTAMP + " DESC");
        List<StockInInfo> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToStockIn(c));
        }
        c.close();
        return list;
    }

    @SuppressLint("Range")
    private BoxInfo cursorToBox(Cursor c) {
        BoxInfo b = new BoxInfo();
        b.id = c.getLong(c.getColumnIndex(BOX_ID));
        b.epc = c.getString(c.getColumnIndex(BOX_EPC));
        b.shortId = c.getString(c.getColumnIndex(BOX_SHORT_ID));
        b.description = c.getString(c.getColumnIndex(BOX_DESCRIPTION));
        b.photoPath = c.getString(c.getColumnIndex(BOX_PHOTO_PATH));
        b.createdAt = c.getString(c.getColumnIndex(BOX_CREATED_AT));
        return b;
    }

    @SuppressLint("Range")
    private ContentInfo cursorToContent(Cursor c) {
        ContentInfo ct = new ContentInfo();
        ct.id = c.getLong(c.getColumnIndex(CONTENT_ID));
        ct.boxEpc = c.getString(c.getColumnIndex(CONTENT_BOX_EPC));
        ct.epc = c.getString(c.getColumnIndex(CONTENT_EPC));
        ct.shortId = c.getString(c.getColumnIndex(CONTENT_SHORT_ID));
        ct.description = c.getString(c.getColumnIndex(CONTENT_DESCRIPTION));
        ct.photoPath = c.getString(c.getColumnIndex(CONTENT_PHOTO_PATH));
        ct.createdAt = c.getString(c.getColumnIndex(CONTENT_CREATED_AT));
        return ct;
    }

    @SuppressLint("Range")
    private CheckoutLogInfo cursorToLog(Cursor c) {
        CheckoutLogInfo l = new CheckoutLogInfo();
        l.id = c.getLong(c.getColumnIndex(LOG_ID));
        l.studentName = c.getString(c.getColumnIndex(LOG_STUDENT_NAME));
        l.studentId = c.getString(c.getColumnIndex(LOG_STUDENT_ID));
        l.boxEpc = c.getString(c.getColumnIndex(LOG_BOX_EPC));
        l.boxShortId = c.getString(c.getColumnIndex(LOG_BOX_SHORT_ID));
        l.status = c.getString(c.getColumnIndex(LOG_STATUS));
        l.missingItems = c.getString(c.getColumnIndex(LOG_MISSING_ITEMS));
        l.checkedItems = c.getString(c.getColumnIndex(LOG_CHECKED_ITEMS));
        l.timestamp = c.getString(c.getColumnIndex(LOG_TIMESTAMP));
        // New columns (may not exist in old DB)
        try { l.itemType = c.getString(c.getColumnIndex(LOG_ITEM_TYPE)); } catch (Exception e) { l.itemType = ""; }
        try { l.parentBoxName = c.getString(c.getColumnIndex(LOG_PARENT_BOX)); } catch (Exception e) { l.parentBoxName = ""; }
        return l;
    }

    @SuppressLint("Range")
    private StockInInfo cursorToStockIn(Cursor c) {
        StockInInfo s = new StockInInfo();
        s.id = c.getLong(c.getColumnIndex(SI_ID));
        s.epc = c.getString(c.getColumnIndex(SI_EPC));
        s.shortId = c.getString(c.getColumnIndex(SI_SHORT_ID));
        s.description = c.getString(c.getColumnIndex(SI_DESCRIPTION));
        // Read photo_paths (JSON array); fallback to legacy photo_path
        String photoPathsJson = "";
        try { photoPathsJson = c.getString(c.getColumnIndex(SI_PHOTO_PATHS)); } catch (Exception e) {}
        if (photoPathsJson == null || photoPathsJson.isEmpty() || "[]".equals(photoPathsJson)) {
            String legacyPath = "";
            try { legacyPath = c.getString(c.getColumnIndex(SI_PHOTO_PATH)); } catch (Exception e) {}
            if (legacyPath != null && !legacyPath.isEmpty()) {
                s.photoPaths = new ArrayList<>();
                s.photoPaths.add(legacyPath);
            } else {
                s.photoPaths = new ArrayList<>();
            }
        } else {
            s.photoPaths = StockInInfo.jsonToPhotoPaths(photoPathsJson);
        }
        // New fields (may not exist in very old DB)
        try { s.category = c.getString(c.getColumnIndex(SI_CATEGORY)); } catch (Exception e) { s.category = ""; }
        try { s.itemNumber = c.getString(c.getColumnIndex(SI_ITEM_NUMBER)); } catch (Exception e) { s.itemNumber = ""; }
        try { s.shelf = c.getString(c.getColumnIndex(SI_SHELF)); } catch (Exception e) { s.shelf = ""; }
        try { s.room = c.getString(c.getColumnIndex(SI_ROOM)); } catch (Exception e) { s.room = ""; }
        if (s.category == null) s.category = "";
        if (s.itemNumber == null) s.itemNumber = "";
        if (s.shelf == null) s.shelf = "";
        if (s.room == null) s.room = "";
        s.boxEpc = c.getString(c.getColumnIndex(SI_BOX_EPC));
        s.type = c.getString(c.getColumnIndex(SI_TYPE));
        s.timestamp = c.getString(c.getColumnIndex(SI_TIMESTAMP));
        return s;
    }
}
