package com.example.uhf.fragment;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.db.BoxInfo;
import com.example.uhf.db.ContentInfo;
import com.example.uhf.db.DatabaseHelper;
import com.example.uhf.db.DisplayItem;
import com.example.uhf.db.StockInInfo;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stock-In Fragment (入库界面).
 * Scan RFID tags and register items into the warehouse.
 * History shown as dynamically-added views in a LinearLayout (ScrollView-compatible).
 */
public class StockInFragment extends KeyDwonFragment {

    private static final String TAG = "StockInFragment";
    private static final int REQUEST_STOCKIN_PHOTO = 300;
    private static final int REQUEST_CAMERA_PERMISSION = 301;

    private UHFMainActivity mContext;
    private DatabaseHelper dbHelper;

    // Scan & Input
    private EditText etStockInEpc, etStockInShortId, etStockInDesc;
    private EditText etStockInCategory, etStockInItemNumber, etStockInShelf, etStockInRoom;
    private Button btnStockInScan;
    private Spinner spStockInBox;
    private TextView tvStockInBoxStatus;

    // Power control
    private SeekBar seekBarPower;
    private TextView tvPower;

    // Multi-Photo
    private Button btnStockInPhoto;
    private TextView tvStockInPhotoCount;
    private RecyclerView rvPhotoThumbnails;
    private PhotoThumbnailAdapter photoAdapter;
    private List<String> currentPhotoPaths = new ArrayList<>();
    private String pendingPhotoPath = null;

    // Actions
    private Button btnStockInAsBox, btnStockInAsContent, btnStockInStandalone, btnStockInClear;

    // History section (LinearLayout-based, with collapse/expand)
    private LinearLayout llStockInHistoryHeader;
    private ImageView ivStockInHistoryArrow;
    private TextView tvStockInCount;
    private TextView tvStockInHistoryEmpty;
    private LinearLayout llStockInHistoryList;
    private Button btnStockInDeleteAll;
    private boolean isHistoryExpanded = true;

    // Grouping section (入库分组: BOX为组、CONTENT为子项、STANDALONE独立)
    private LinearLayout llStockInGroupingHeader;
    private ImageView ivStockInGroupingArrow;
    private TextView tvStockInGroupingCount;
    private TextView tvStockInGroupingEmpty;
    private LinearLayout llStockInGroupingList;
    private boolean isGroupingExpanded = true;

    // Data (入库记录 - flat list)
    private List<StockInInfo> stockInRecords = new ArrayList<>();

    // Data (入库分组 - grouped)
    private List<DisplayItem> groupList = new ArrayList<>();
    private Map<String, List<DisplayItem>> childrenMap = new LinkedHashMap<>();

    // Track which groups are expanded in the grouping view
    private Set<String> expandedGroupEpcs = new HashSet<>();

    // Box list for spinner
    private List<BoxInfo> boxList = new ArrayList<>();
    private String selectedBoxEpc = "";

    // ScrollView for saving/restoring scroll position
    private ScrollView scrollStockIn;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stockin, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        dbHelper = DatabaseHelper.getInstance(mContext);

        initViews();
        loadBoxList();
        loadHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Save scroll position before reload (views will be rebuilt)
        final int savedScrollY = scrollStockIn != null ? scrollStockIn.getScrollY() : 0;

        loadBoxList();
        loadHistory();

        // Restore scroll position after views are rebuilt and laid out
        if (scrollStockIn != null && savedScrollY > 0) {
            scrollStockIn.post(() -> scrollStockIn.scrollTo(0, savedScrollY));
        }

        // Refresh current power level
        RFIDWithUHFUART reader = mContext.getReader();
        int power = reader != null ? reader.getPower() : -1;
        if (power > 0 && power <= 30) {
            seekBarPower.setProgress(power - 1);
            tvPower.setText(getString(R.string.power_label, power));
        }
    }

    /**
     * Initialize power SeekBar with current power and set change listener.
     */
    private void initPowerSeekBar() {
        // Read current power and set initial position
        RFIDWithUHFUART reader = mContext.getReader();
        int power = reader != null ? reader.getPower() : -1;
        if (power > 0 && power <= 30) {
            seekBarPower.setProgress(power - 1);
            tvPower.setText(getString(R.string.power_label, power));
        }

        seekBarPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int powerValue = progress + 1;
                tvPower.setText(getString(R.string.power_label, powerValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int powerValue = seekBar.getProgress() + 1;
                RFIDWithUHFUART reader = mContext.getReader();
                if (reader != null && reader.setPower(powerValue)) {
                    Log.d(TAG, "Power set to " + powerValue + " dBm");
                } else {
                    Log.e(TAG, "Failed to set power to " + powerValue);
                }
            }
        });
    }

    private void initViews() {
        View v = getView();

        etStockInEpc = v.findViewById(R.id.etStockInEpc);
        etStockInShortId = v.findViewById(R.id.etStockInShortId);
        etStockInDesc = v.findViewById(R.id.etStockInDesc);
        etStockInCategory = v.findViewById(R.id.etStockInCategory);
        etStockInItemNumber = v.findViewById(R.id.etStockInItemNumber);
        etStockInShelf = v.findViewById(R.id.etStockInShelf);
        etStockInRoom = v.findViewById(R.id.etStockInRoom);
        btnStockInScan = v.findViewById(R.id.btnStockInScan);

        // Power SeekBar
        seekBarPower = v.findViewById(R.id.seekBarStockInPower);
        tvPower = v.findViewById(R.id.tvStockInPower);
        initPowerSeekBar();

        spStockInBox = v.findViewById(R.id.spStockInBox);
        tvStockInBoxStatus = v.findViewById(R.id.tvStockInBoxStatus);

        btnStockInPhoto = v.findViewById(R.id.btnStockInPhoto);
        tvStockInPhotoCount = v.findViewById(R.id.tvStockInPhotoCount);
        rvPhotoThumbnails = v.findViewById(R.id.rvPhotoThumbnails);

        btnStockInAsBox = v.findViewById(R.id.btnStockInAsBox);
        btnStockInAsContent = v.findViewById(R.id.btnStockInAsContent);
        btnStockInStandalone = v.findViewById(R.id.btnStockInStandalone);
        btnStockInClear = v.findViewById(R.id.btnStockInClear);

        // History section views
        llStockInHistoryHeader = v.findViewById(R.id.llStockInHistoryHeader);
        ivStockInHistoryArrow = v.findViewById(R.id.ivStockInHistoryArrow);
        tvStockInCount = v.findViewById(R.id.tvStockInCount);
        tvStockInHistoryEmpty = v.findViewById(R.id.tvStockInHistoryEmpty);
        llStockInHistoryList = v.findViewById(R.id.llStockInHistoryList);
        btnStockInDeleteAll = v.findViewById(R.id.btnStockInDeleteAll);

        // Grouping section views
        llStockInGroupingHeader = v.findViewById(R.id.llStockInGroupingHeader);
        ivStockInGroupingArrow = v.findViewById(R.id.ivStockInGroupingArrow);
        tvStockInGroupingCount = v.findViewById(R.id.tvStockInGroupingCount);
        tvStockInGroupingEmpty = v.findViewById(R.id.tvStockInGroupingEmpty);
        llStockInGroupingList = v.findViewById(R.id.llStockInGroupingList);

        // Setup photo RecyclerView (horizontal)
        LinearLayoutManager photoLayoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        rvPhotoThumbnails.setLayoutManager(photoLayoutManager);
        photoAdapter = new PhotoThumbnailAdapter();
        rvPhotoThumbnails.setAdapter(photoAdapter);
        updatePhotoCount();

        // Listeners
        btnStockInScan.setOnClickListener(view -> scanTag());
        btnStockInPhoto.setOnClickListener(view -> takePhoto());
        btnStockInAsBox.setOnClickListener(view -> registerAsBox());
        btnStockInAsContent.setOnClickListener(view -> registerAsContent());
        btnStockInStandalone.setOnClickListener(view -> registerStandalone());
        btnStockInClear.setOnClickListener(view -> clearFields());
        btnStockInDeleteAll.setOnClickListener(view -> confirmDeleteAll());

        // History header click -> toggle collapse/expand
        llStockInHistoryHeader.setOnClickListener(vv -> toggleHistoryVisibility());

        // Grouping header click -> toggle collapse/expand
        llStockInGroupingHeader.setOnClickListener(vv -> toggleGroupingVisibility());

        scrollStockIn = v.findViewById(R.id.scrollStockIn);

        spStockInBox.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedBoxEpc = "";
                    tvStockInBoxStatus.setText(R.string.stockin_no_box);
                } else {
                    BoxInfo box = boxList.get(position - 1);
                    selectedBoxEpc = box.epc;
                    tvStockInBoxStatus.setText("Box: " + box.toString());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // ==================== Scan ====================

    private void scanTag() {
        RFIDWithUHFUART reader = mContext.getReader();
        UHFTAGInfo tag = reader != null ? reader.inventorySingleTag() : null;
        if (tag != null) {
            String tid = getTagId(tag);

            // Clear all fields first to remove any residual data from previous scan
            clearFields();

            // Set the newly scanned TID
            etStockInEpc.setText(tid);
            mContext.playSound(1);

            // Auto-fill from latest stock-in record if exists
            StockInInfo latest = dbHelper.getLatestStockIn(tid);
            if (latest != null) {
                autoFillFromRecord(latest);
                mContext.showToast(R.string.stockin_auto_filled);
            }
        } else {
            mContext.showToast(R.string.uhf_msg_inventory_fail);
        }
    }

    private void autoFillFromRecord(StockInInfo record) {
        etStockInShortId.setText(record.shortId != null ? record.shortId : "");
        etStockInDesc.setText(record.description != null ? record.description : "");
        etStockInCategory.setText(record.category != null ? record.category : "");
        etStockInItemNumber.setText(record.itemNumber != null ? record.itemNumber : "");
        etStockInShelf.setText(record.shelf != null ? record.shelf : "");
        etStockInRoom.setText(record.room != null ? record.room : "");

        currentPhotoPaths.clear();
        if (record.photoPaths != null) {
            for (String p : record.photoPaths) {
                if (p != null && !p.isEmpty() && new File(p).exists()) {
                    currentPhotoPaths.add(p);
                }
            }
        }
        photoAdapter.notifyDataSetChanged();
        updatePhotoCount();

        if (record.boxEpc != null && !record.boxEpc.isEmpty()) {
            for (int i = 0; i < boxList.size(); i++) {
                if (boxList.get(i).epc.equals(record.boxEpc)) {
                    spStockInBox.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    // ==================== Camera / Photo ====================

    private void takePhoto() {
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(mContext,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            File photoFile = createImageFile();
            pendingPhotoPath = photoFile.getAbsolutePath();
            Uri photoUri = FileProvider.getUriForFile(mContext,
                    mContext.getPackageName() + ".fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(takePictureIntent, REQUEST_STOCKIN_PHOTO);
        } catch (Exception ex) {
            Log.e(TAG, "Error launching camera", ex);
            Toast.makeText(mContext, "Camera error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "UHF_stockin_" + timeStamp + "_";
        File storageDir = new File(mContext.getCacheDir(), "UHF_photos");
        if (!storageDir.exists()) storageDir.mkdirs();
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(mContext, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_STOCKIN_PHOTO && pendingPhotoPath != null) {
                currentPhotoPaths.add(pendingPhotoPath);
                photoAdapter.notifyDataSetChanged();
                updatePhotoCount();
                rvPhotoThumbnails.scrollToPosition(currentPhotoPaths.size() - 1);
                pendingPhotoPath = null;
                mContext.showToast(R.string.kitting_photo_saved);
            }
        }
    }

    private void updatePhotoCount() {
        int count = currentPhotoPaths.size();
        tvStockInPhotoCount.setText(getString(R.string.stockin_photos_count, count));
        rvPhotoThumbnails.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    // ==================== Register Actions ====================
    // Each registration creates exactly ONE independent stock_in record.

    private void registerAsBox() {
        String epc = getEpc();
        if (epc == null) return;
        String shortId = etStockInShortId.getText().toString().trim();
        String desc = etStockInDesc.getText().toString().trim();
        String category = etStockInCategory.getText().toString().trim();
        String itemNumber = etStockInItemNumber.getText().toString().trim();
        String shelf = etStockInShelf.getText().toString().trim();
        String room = etStockInRoom.getText().toString().trim();
        String photoPathsJson = buildPhotoPathsJson();
        String firstPhoto = currentPhotoPaths.isEmpty() ? "" : currentPhotoPaths.get(0);

        // Purge old data before re-registering
        dbHelper.purgeAllDataByEpc(epc);

        long result = dbHelper.insertBox(epc, shortId, desc, firstPhoto);
        if (result != -1) {
            dbHelper.insertStockIn(epc, shortId, desc, photoPathsJson,
                    category, itemNumber, shelf, room, "", "BOX");
            mContext.showToast(R.string.stockin_registered);
            clearFields();
            loadBoxList();
            loadHistory();
        } else {
            mContext.showToast(R.string.stockin_register_fail);
        }
    }

    private void registerAsContent() {
        String epc = getEpc();
        if (epc == null) return;
        if (selectedBoxEpc.isEmpty()) {
            mContext.showToast(R.string.kitting_no_box);
            return;
        }
        String shortId = etStockInShortId.getText().toString().trim();
        String desc = etStockInDesc.getText().toString().trim();
        String category = etStockInCategory.getText().toString().trim();
        String itemNumber = etStockInItemNumber.getText().toString().trim();
        String shelf = etStockInShelf.getText().toString().trim();
        String room = etStockInRoom.getText().toString().trim();
        String photoPathsJson = buildPhotoPathsJson();

        // Purge old data before re-registering
        dbHelper.purgeAllDataByEpc(epc);

        BoxInfo box = dbHelper.getBoxByEpc(selectedBoxEpc);
        if (box == null) dbHelper.insertBox(selectedBoxEpc, "", "", "");
        if (dbHelper.contentExists(selectedBoxEpc, epc)) {
            mContext.showToast(R.string.stockin_duplicate);
            return;
        }
        String firstPhoto = currentPhotoPaths.isEmpty() ? "" : currentPhotoPaths.get(0);
        long result = dbHelper.insertContent(selectedBoxEpc, epc, shortId, desc, firstPhoto);
        if (result != -1) {
            dbHelper.insertStockIn(epc, shortId, desc, photoPathsJson,
                    category, itemNumber, shelf, room, selectedBoxEpc, "CONTENT");
            mContext.showToast(R.string.stockin_registered);
            clearFields();
            loadHistory();
        } else {
            mContext.showToast(R.string.stockin_register_fail);
        }
    }

    private void registerStandalone() {
        String epc = getEpc();
        if (epc == null) return;
        String shortId = etStockInShortId.getText().toString().trim();
        String desc = etStockInDesc.getText().toString().trim();
        String category = etStockInCategory.getText().toString().trim();
        String itemNumber = etStockInItemNumber.getText().toString().trim();
        String shelf = etStockInShelf.getText().toString().trim();
        String room = etStockInRoom.getText().toString().trim();
        String photoPathsJson = buildPhotoPathsJson();

        // Purge old data before re-registering
        dbHelper.purgeAllDataByEpc(epc);

        long result = dbHelper.insertStockIn(epc, shortId, desc, photoPathsJson,
                category, itemNumber, shelf, room, "", "STANDALONE");
        if (result != -1) {
            mContext.showToast(R.string.stockin_registered);
            clearFields();
            loadHistory();
        } else {
            mContext.showToast(R.string.stockin_register_fail);
        }
    }

    private String getEpc() {
        String epc = etStockInEpc.getText().toString().trim();
        if (epc.isEmpty()) {
            mContext.showToast(R.string.stockin_no_epc);
            return null;
        }
        return epc;
    }

    private String buildPhotoPathsJson() {
        if (currentPhotoPaths.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < currentPhotoPaths.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(currentPhotoPaths.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private void clearFields() {
        etStockInEpc.setText("");
        etStockInShortId.setText("");
        etStockInDesc.setText("");
        etStockInCategory.setText("");
        etStockInItemNumber.setText("");
        etStockInShelf.setText("");
        etStockInRoom.setText("");
        currentPhotoPaths.clear();
        photoAdapter.notifyDataSetChanged();
        updatePhotoCount();
        spStockInBox.setSelection(0);
    }

    // ==================== Delete ====================

    private void confirmDeleteAll() {
        new AlertDialog.Builder(mContext)
                .setMessage(R.string.stockin_delete_all_confirm)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    dbHelper.deleteAllStockIns();
                    mContext.showToast(R.string.stockin_all_deleted);
                    loadBoxList();
                    loadHistory();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDeleteItem(StockInInfo record) {
        new AlertDialog.Builder(mContext)
                .setMessage(R.string.stockin_delete_confirm)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    // deleteStockIn cascades to boxes/contents + checkout_logs
                    dbHelper.deleteStockIn(record.id);
                    mContext.showToast(R.string.stockin_deleted);
                    loadBoxList();
                    loadHistory();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== Data Loading ====================

    private void loadBoxList() {
        // Save current selection before refreshing
        String savedBoxEpc = selectedBoxEpc;

        boxList.clear();
        boxList.addAll(dbHelper.getAllBoxes());
        List<String> spinnerItems = new ArrayList<>();
        spinnerItems.add("-- " + getString(R.string.stockin_select_box) + " --");
        for (BoxInfo box : boxList) spinnerItems.add(box.toString());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext,
                android.R.layout.simple_spinner_item, spinnerItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStockInBox.setAdapter(adapter);

        // Restore previous selection
        if (savedBoxEpc != null && !savedBoxEpc.isEmpty()) {
            for (int i = 0; i < boxList.size(); i++) {
                if (boxList.get(i).epc.equals(savedBoxEpc)) {
                    spStockInBox.setSelection(i + 1);
                    break;
                }
            }
        }
    }

    /**
     * Load all stock-in records sorted by timestamp DESC (newest first).
     * Also loads the grouping view (BOX as groups, CONTENT as children, STANDALONE standalone).
     */
    private void loadHistory() {
        // Load flat records list
        stockInRecords.clear();
        stockInRecords.addAll(dbHelper.getAllStockIns());
        renderHistoryList();

        int totalCount = dbHelper.getStockInCount();
        tvStockInCount.setText(getString(R.string.stockin_item_count, totalCount));

        // Load grouping view
        loadGroupingList();
    }

    /**
     * Render all stock-in records as a flat list sorted by time (newest first).
     * Each item shows: name, timestamp, type (BOX/CONTENT/STANDALONE).
     */
    private void renderHistoryList() {
        llStockInHistoryList.removeAllViews();

        if (stockInRecords.isEmpty()) {
            tvStockInHistoryEmpty.setVisibility(View.VISIBLE);
            llStockInHistoryList.setVisibility(View.GONE);
            return;
        }

        tvStockInHistoryEmpty.setVisibility(View.GONE);
        llStockInHistoryList.setVisibility(View.VISIBLE);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (StockInInfo record : stockInRecords) {
            View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_si_group, llStockInHistoryList, false);
            bindRecordView(itemView, record, sdf);
            llStockInHistoryList.addView(itemView);
        }
    }

    private void bindRecordView(View view, StockInInfo record, SimpleDateFormat sdf) {
        TextView tvType = view.findViewById(R.id.tvSiGroupType);
        TextView tvName = view.findViewById(R.id.tvSiGroupName);
        TextView tvBorrow = view.findViewById(R.id.tvSiGroupBorrow);
        TextView tvTimestamp = view.findViewById(R.id.tvSiGroupTimestamp);
        TextView tvInfo = view.findViewById(R.id.tvSiGroupInfo);
        Button btnDelete = view.findViewById(R.id.btnSiGroupDelete);

        // Type icon based on record type
        switch (record.type) {
            case "BOX":       tvType.setText("\uD83D\uDCE6"); break;
            case "CONTENT":   tvType.setText("\uD83D\uDCC4"); break;
            case "STANDALONE": tvType.setText("\uD83D\uDCCB"); break;
            default:          tvType.setText("\uD83D\uDCCB"); break;
        }

        // Name (clickable -> detail page with fromStockIn=true for editable mode)
        String displayName = (record.shortId != null && !record.shortId.isEmpty()) ? record.shortId
                : (record.description != null && !record.description.isEmpty()) ? record.description
                : record.epc;
        tvName.setText(displayName);
        tvName.setTextColor(0xFF333333);
        final String detailEpc = record.epc;
        tvName.setOnClickListener(v -> mContext.openItemDetail(detailEpc, true));

        // Timestamp
        String timeStr = "";
        if (record.timestamp != null && !record.timestamp.isEmpty()) {
            try {
                long ts = Long.parseLong(record.timestamp);
                timeStr = sdf.format(new Date(ts));
            } catch (NumberFormatException e) {
                timeStr = record.timestamp;
            }
        }
        tvTimestamp.setText(timeStr);

        // Borrow status badge
        String borrowStatus = dbHelper.getItemBorrowStatus(record.epc);
        if ("BORROWED".equals(borrowStatus)) {
            tvBorrow.setText(R.string.warehouse_item_borrowed); tvBorrow.setTextColor(0xFFFF5722);
        } else {
            tvBorrow.setText(R.string.warehouse_item_in_stock); tvBorrow.setTextColor(0xFF4CAF50);
        }

        // Info line: TID + type label + optional child count for BOX
        String infoText = "TID: " + truncateEpc(record.epc) + "  |  " + record.type;
        if ("BOX".equals(record.type)) {
            int childCount = dbHelper.getBoxContentCount(record.epc);
            infoText += "  |  " + getString(R.string.warehouse_contents_count, childCount);
        }
        tvInfo.setText(infoText);

        btnDelete.setOnClickListener(v -> confirmDeleteItem(record));
    }

    private String truncateEpc(String epc) {
        if (epc == null) return "";
        return epc.length() > 12 ? epc.substring(0, 12) + "..." : epc;
    }

    // ==================== History Collapse/Expand ====================

    private void toggleHistoryVisibility() {
        if (isHistoryExpanded) {
            // Collapse: hide list + empty text
            llStockInHistoryList.setVisibility(View.GONE);
            tvStockInHistoryEmpty.setVisibility(View.GONE);
            // Rotate arrow from 180 (down) to 0 (up/collapsed)
            RotateAnimation rotate = new RotateAnimation(180f, 0f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotate.setDuration(250);
            rotate.setFillAfter(true);
            ivStockInHistoryArrow.startAnimation(rotate);
            isHistoryExpanded = false;
        } else {
            // Expand: show list or empty text
            if (stockInRecords.isEmpty()) {
                tvStockInHistoryEmpty.setVisibility(View.VISIBLE);
                llStockInHistoryList.setVisibility(View.GONE);
            } else {
                tvStockInHistoryEmpty.setVisibility(View.GONE);
                llStockInHistoryList.setVisibility(View.VISIBLE);
            }
            // Rotate arrow from 0 (up/collapsed) to 180 (down)
            RotateAnimation rotate = new RotateAnimation(0f, 180f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotate.setDuration(250);
            rotate.setFillAfter(true);
            ivStockInHistoryArrow.startAnimation(rotate);
            isHistoryExpanded = true;
        }
    }

    // ==================== Grouping (入库分组: ExpandableListView-style) ====================

    /**
     * Load grouping data: BOX as groups, CONTENT as children, STANDALONE as independent groups.
     */
    private void loadGroupingList() {
        groupList.clear();
        childrenMap.clear();

        // Boxes as groups with their contents as children
        List<BoxInfo> boxes = dbHelper.getAllBoxes();
        for (BoxInfo box : boxes) {
            DisplayItem group = DisplayItem.fromBox(box);
            group.borrowStatus = dbHelper.getItemBorrowStatus(box.epc);
            List<ContentInfo> contents = dbHelper.getContentsByBoxEpc(box.epc);
            List<DisplayItem> children = new ArrayList<>();
            for (ContentInfo c : contents) {
                DisplayItem child = DisplayItem.fromContent(c);
                child.borrowStatus = dbHelper.getItemBorrowStatus(c.epc);
                child.boxName = group.name;
                children.add(child);
            }
            group.totalContents = children.size() + 1;
            groupList.add(group);
            childrenMap.put(box.epc, children);
        }

        // Standalone items as childless groups
        List<StockInInfo> standalones = dbHelper.getStandaloneItems();
        for (StockInInfo si : standalones) {
            DisplayItem item = DisplayItem.fromStandalone(si);
            item.borrowStatus = dbHelper.getItemBorrowStatus(si.epc);
            groupList.add(item);
        }

        renderGroupingList();

        int totalCount = dbHelper.getStockInCount();
        tvStockInGroupingCount.setText(getString(R.string.stockin_item_count, totalCount));
    }

    /**
     * Render group + child items into llStockInGroupingList LinearLayout.
     * Each group gets a header view, followed by child views indented underneath.
     */
    private void renderGroupingList() {
        llStockInGroupingList.removeAllViews();

        if (groupList.isEmpty()) {
            tvStockInGroupingEmpty.setVisibility(View.VISIBLE);
            llStockInGroupingList.setVisibility(View.GONE);
            return;
        }

        tvStockInGroupingEmpty.setVisibility(View.GONE);
        llStockInGroupingList.setVisibility(View.VISIBLE);

        for (DisplayItem group : groupList) {
            View groupView = LayoutInflater.from(mContext).inflate(R.layout.item_si_group, llStockInGroupingList, false);
            bindGroupView(groupView, group);
            llStockInGroupingList.addView(groupView);

            if (group.type == DisplayItem.TYPE_BOX) {
                List<DisplayItem> children = childrenMap.getOrDefault(group.epc, new ArrayList<>());
                // Create a container to hold all children for this group
                final LinearLayout childContainer = new LinearLayout(mContext);
                childContainer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                childContainer.setOrientation(LinearLayout.VERTICAL);
                childContainer.setId(View.generateViewId());

                for (DisplayItem child : children) {
                    View childView = LayoutInflater.from(mContext).inflate(R.layout.item_si_child, childContainer, false);
                    bindChildView(childView, child);
                    childContainer.addView(childView);
                }

                // Initially show/hide based on expanded state
                boolean isExpanded = expandedGroupEpcs.contains(group.epc);
                childContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
                llStockInGroupingList.addView(childContainer);

                // Toggle on group view click (but not on delete button click)
                final String epc = group.epc;
                final ImageView ivArrow = groupView.findViewById(R.id.ivSiGroupArrow);
                // Initialize arrow rotation based on current expanded state
                ivArrow.setRotation(expandedGroupEpcs.contains(epc) ? 180f : 0f);
                groupView.setOnClickListener(v -> {
                    boolean currentlyExpanded = expandedGroupEpcs.contains(epc);
                    if (currentlyExpanded) {
                        expandedGroupEpcs.remove(epc);
                        childContainer.setVisibility(View.GONE);
                        ivArrow.setRotation(0f);
                    } else {
                        expandedGroupEpcs.add(epc);
                        childContainer.setVisibility(View.VISIBLE);
                        ivArrow.setRotation(180f);
                    }
                });
            }
        }
    }

    private void bindGroupView(View view, DisplayItem item) {
        TextView tvType = view.findViewById(R.id.tvSiGroupType);
        TextView tvName = view.findViewById(R.id.tvSiGroupName);
        TextView tvBorrow = view.findViewById(R.id.tvSiGroupBorrow);
        TextView tvTimestamp = view.findViewById(R.id.tvSiGroupTimestamp);
        TextView tvInfo = view.findViewById(R.id.tvSiGroupInfo);
        Button btnDelete = view.findViewById(R.id.btnSiGroupDelete);
        ImageView ivArrow = view.findViewById(R.id.ivSiGroupArrow);

        tvType.setText(item.type == DisplayItem.TYPE_BOX ? "\uD83D\uDCE6" : "\uD83D\uDCCB");

        // Name (clickable -> detail page with fromStockIn=true)
        tvName.setText(item.name);
        tvName.setTextColor(0xFF333333);
        final String detailEpc = item.epc;
        tvName.setOnClickListener(v -> mContext.openItemDetail(detailEpc, true));

        // Hide timestamp in grouping view (not relevant for grouped display)
        tvTimestamp.setVisibility(View.GONE);

        // Borrow status badge
        if ("BORROWED".equals(item.borrowStatus)) {
            tvBorrow.setText(R.string.warehouse_item_borrowed); tvBorrow.setTextColor(0xFFFF5722);
        } else {
            tvBorrow.setText(R.string.warehouse_item_in_stock); tvBorrow.setTextColor(0xFF4CAF50);
        }

        // Info line
        if (item.type == DisplayItem.TYPE_BOX) {
            int childCount = childrenMap.getOrDefault(item.epc, new ArrayList<>()).size();
            tvInfo.setText("TID: " + truncateEpc(item.epc)
                    + "  |  " + getString(R.string.warehouse_contents_count, childCount));
            ivArrow.setVisibility(View.VISIBLE);
        } else {
            tvInfo.setText("TID: " + truncateEpc(item.epc));
            ivArrow.setVisibility(View.GONE);
        }

        btnDelete.setOnClickListener(v -> confirmDeleteGroupingItem(item));
    }

    private void bindChildView(View view, DisplayItem item) {
        TextView tvName = view.findViewById(R.id.tvSiChildName);
        TextView tvEpc = view.findViewById(R.id.tvSiChildEpc);
        TextView tvBorrow = view.findViewById(R.id.tvSiChildBorrow);
        Button btnDelete = view.findViewById(R.id.btnSiChildDelete);

        tvName.setText(item.name);
        tvName.setTextColor(0xFF333333);
        final String detailEpc = item.epc;
        tvName.setOnClickListener(v -> mContext.openItemDetail(detailEpc, true));

        tvEpc.setText(truncateEpc(item.epc));

        if ("BORROWED".equals(item.borrowStatus)) {
            tvBorrow.setText(R.string.warehouse_item_borrowed); tvBorrow.setTextColor(0xFFFF5722);
        } else {
            tvBorrow.setText(R.string.warehouse_item_in_stock); tvBorrow.setTextColor(0xFF4CAF50);
        }

        btnDelete.setOnClickListener(v -> confirmDeleteGroupingItem(item));
    }

    private void confirmDeleteGroupingItem(DisplayItem item) {
        new AlertDialog.Builder(mContext)
                .setMessage(R.string.stockin_delete_confirm)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (item.type == DisplayItem.TYPE_BOX) {
                        dbHelper.deleteBox(item.epc);
                        dbHelper.deleteStockInsByEpc(item.epc);
                    } else if (item.type == DisplayItem.TYPE_CONTENT) {
                        if (!item.boxEpc.isEmpty()) {
                            dbHelper.deleteContentByEpc(item.boxEpc, item.epc);
                        }
                        dbHelper.deleteStockInsByEpc(item.epc);
                    } else {
                        dbHelper.deleteStockInsByEpc(item.epc);
                    }
                    // Also clean up checkout_logs for this item's EPC
                    dbHelper.deleteCheckoutLogsByEpc(item.epc);
                    mContext.showToast(R.string.stockin_deleted);
                    loadBoxList();
                    loadHistory();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== Grouping Collapse/Expand ====================

    private void toggleGroupingVisibility() {
        if (isGroupingExpanded) {
            llStockInGroupingList.setVisibility(View.GONE);
            tvStockInGroupingEmpty.setVisibility(View.GONE);
            RotateAnimation rotate = new RotateAnimation(180f, 0f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotate.setDuration(250);
            rotate.setFillAfter(true);
            ivStockInGroupingArrow.startAnimation(rotate);
            isGroupingExpanded = false;
        } else {
            if (groupList.isEmpty()) {
                tvStockInGroupingEmpty.setVisibility(View.VISIBLE);
                llStockInGroupingList.setVisibility(View.GONE);
            } else {
                tvStockInGroupingEmpty.setVisibility(View.GONE);
                llStockInGroupingList.setVisibility(View.VISIBLE);
            }
            RotateAnimation rotate = new RotateAnimation(0f, 180f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotate.setDuration(250);
            rotate.setFillAfter(true);
            ivStockInGroupingArrow.startAnimation(rotate);
            isGroupingExpanded = true;
        }
    }

    /**
     * Get unique tag identifier: prefer TID, fallback to EPC.
     */
    private String getTagId(UHFTAGInfo tag) {
        String tid = tag.getTid();
        if (tid != null && !tid.isEmpty() && !tid.equals("000000000000000000000000")) {
            return tid;
        }
        return tag.getEPC();
    }

    @Override
    public void myOnKeyDwon() {
        scanTag();
    }

    // ==================== Photo Thumbnail Adapter ====================

    private class PhotoThumbnailAdapter extends RecyclerView.Adapter<PhotoThumbnailAdapter.PhotoVH> {
        @NonNull
        @Override
        public PhotoVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(mContext).inflate(R.layout.item_photo_thumbnail, parent, false);
            return new PhotoVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoVH holder, int position) {
            String path = currentPhotoPaths.get(position);
            File f = new File(path);
            if (f.exists()) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, options);
                    int sampleSize = 1;
                    while (options.outWidth / sampleSize > 200) sampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = sampleSize;
                    Bitmap bmp = BitmapFactory.decodeFile(path, options);
                    holder.ivThumbnail.setImageBitmap(bmp);
                } catch (Exception e) {
                    holder.ivThumbnail.setImageDrawable(null);
                }
            }
            holder.ivRemove.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos >= 0 && pos < currentPhotoPaths.size()) {
                    currentPhotoPaths.remove(pos);
                    notifyDataSetChanged();
                    updatePhotoCount();
                }
            });
        }

        @Override
        public int getItemCount() { return currentPhotoPaths.size(); }

        class PhotoVH extends RecyclerView.ViewHolder {
            ImageView ivThumbnail, ivRemove;
            PhotoVH(@NonNull View itemView) {
                super(itemView);
                ivThumbnail = itemView.findViewById(R.id.ivPhotoThumbnail);
                ivRemove = itemView.findViewById(R.id.ivPhotoRemove);
            }
        }
    }
}
