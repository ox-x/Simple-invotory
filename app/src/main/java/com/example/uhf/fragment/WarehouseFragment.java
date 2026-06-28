package com.example.uhf.fragment;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.db.BoxInfo;
import com.example.uhf.db.CheckoutLogInfo;
import com.example.uhf.db.ContentInfo;
import com.example.uhf.db.DatabaseHelper;
import com.example.uhf.db.DisplayItem;
import com.example.uhf.db.StockInInfo;

import com.example.uhf.tools.ExcelUtils;
import com.example.uhf.tools.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Warehouse Management Fragment (仓库管理界面).
 * Shows all items grouped by box with filter + search + grid/list view toggle.
 */
public class WarehouseFragment extends KeyDwonFragment {

    private static final String TAG = "WarehouseFragment";
    private static final int FILTER_ALL = 0;
    private static final int FILTER_IN_STOCK = 1;
    private static final int FILTER_BORROWED = 2;

    private UHFMainActivity mContext;
    private DatabaseHelper dbHelper;

    // Search
    private EditText etSearch;
    private String searchQuery = "";

    // Stats
    private TextView tvStatBoxes, tvStatItems, tvStatContents, tvStatInStock, tvStatBorrowed, tvStatStandalone;

    // Filter buttons
    private Button btnFilterAll, btnFilterInStock, btnFilterBorrowed;
    private int currentFilter = FILTER_ALL;

    // View toggle
    private Button btnViewToggle;
    private boolean isGridViewMode = false;

    // Export
    private Button btnWhExport;

    // List view (ExpandableListView)
    private ExpandableListView elvWhList;
    private TextView tvEmpty;
    private WarehouseListAdapter listAdapter;

    // Grid view
    private RecyclerView rvGridView;
    private TextView tvGridEmpty;
    private GridAdapter gridAdapter;

    // Data (all items, before filtering)
    private List<DisplayItem> allGroups = new ArrayList<>();
    private Map<String, List<DisplayItem>> allChildren = new LinkedHashMap<>();

    // Data (filtered)
    private List<DisplayItem> filteredGroups = new ArrayList<>();
    private Map<String, List<DisplayItem>> filteredChildren = new LinkedHashMap<>();

    // Flat list of all filtered items (for grid view)
    private List<DisplayItem> flatFilteredItems = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_warehouse, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        dbHelper = DatabaseHelper.getInstance(mContext);
        initViews();
        loadData();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void initViews() {
        View v = getView();

        // Search
        etSearch = v.findViewById(R.id.etWhSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase();
                applyFilter();
            }
        });

        // Stats
        tvStatBoxes = v.findViewById(R.id.tvWhStatBoxes);
        tvStatItems = v.findViewById(R.id.tvWhStatItems);
        tvStatInStock = v.findViewById(R.id.tvWhStatInStock);
        tvStatBorrowed = v.findViewById(R.id.tvWhStatBorrowed);
        tvStatStandalone = v.findViewById(R.id.tvWhStatStandalone);
        tvStatContents = v.findViewById(R.id.tvWhStatContents);

        // Filter buttons
        btnFilterAll = v.findViewById(R.id.btnWhFilterAll);
        btnFilterInStock = v.findViewById(R.id.btnWhFilterInStock);
        btnFilterBorrowed = v.findViewById(R.id.btnWhFilterBorrowed);
        btnFilterAll.setOnClickListener(vv -> setFilter(FILTER_ALL));
        btnFilterInStock.setOnClickListener(vv -> setFilter(FILTER_IN_STOCK));
        btnFilterBorrowed.setOnClickListener(vv -> setFilter(FILTER_BORROWED));

        // View toggle
        btnViewToggle = v.findViewById(R.id.btnWhViewToggle);
        btnViewToggle.setOnClickListener(vv -> toggleViewMode());

        // Export
        btnWhExport = v.findViewById(R.id.btnWhExport);
        btnWhExport.setOnClickListener(vv -> exportWarehouseData());

        // List view
        elvWhList = v.findViewById(R.id.elvWhList);
        tvEmpty = v.findViewById(R.id.tvWhEmpty);
        listAdapter = new WarehouseListAdapter();
        elvWhList.setAdapter(listAdapter);
        // Always consume default group click — we handle it manually in getGroupView
        elvWhList.setOnGroupClickListener((parent, view, groupPosition, id) -> true);

        // Grid view
        rvGridView = v.findViewById(R.id.rvWhGridView);
        tvGridEmpty = v.findViewById(R.id.tvWhGridEmpty);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(mContext, 3);
        rvGridView.setLayoutManager(gridLayoutManager);
        gridAdapter = new GridAdapter();
        rvGridView.setAdapter(gridAdapter);
    }

    // ==================== View Toggle ====================

    private void toggleViewMode() {
        isGridViewMode = !isGridViewMode;
        if (isGridViewMode) {
            elvWhList.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.GONE);
            rvGridView.setVisibility(View.VISIBLE);
            btnViewToggle.setText(R.string.wh_view_list);
            renderGrid();
        } else {
            rvGridView.setVisibility(View.GONE);
            tvGridEmpty.setVisibility(View.GONE);
            elvWhList.setVisibility(View.VISIBLE);
            btnViewToggle.setText(R.string.wh_view_grid);
            renderList();
        }
    }

    // ==================== Data Loading ====================

    private void loadData() {
        allGroups.clear();
        allChildren.clear();

        // Build a map of EPC -> latest StockInInfo for enrichment
        Map<String, StockInInfo> stockInMap = new LinkedHashMap<>();
        for (StockInInfo si : dbHelper.getAllStockIns()) {
            // Keep the latest (first encountered since getAllStockIns returns DESC by timestamp)
            if (!stockInMap.containsKey(si.epc)) {
                stockInMap.put(si.epc, si);
            }
        }

        // Boxes as groups with their contents as children
        List<BoxInfo> boxes = dbHelper.getAllBoxes();
        for (BoxInfo box : boxes) {
            DisplayItem group = DisplayItem.fromBox(box);
            group.borrowStatus = dbHelper.getItemBorrowStatus(box.epc);
            enrichFromStockIn(group, stockInMap.get(box.epc));

            List<ContentInfo> contents = dbHelper.getContentsByBoxEpc(box.epc);
            List<DisplayItem> children = new ArrayList<>();
            for (ContentInfo c : contents) {
                DisplayItem child = DisplayItem.fromContent(c);
                child.borrowStatus = dbHelper.getItemBorrowStatus(c.epc);
                child.boxName = group.name;
                enrichFromStockIn(child, stockInMap.get(c.epc));
                children.add(child);
            }
            group.totalContents = children.size() + 1;
            allGroups.add(group);
            allChildren.put(box.epc, children);
        }

        // Standalone items as childless groups
        List<StockInInfo> standalones = dbHelper.getStandaloneItems();
        for (StockInInfo si : standalones) {
            DisplayItem item = DisplayItem.fromStandalone(si);
            item.borrowStatus = dbHelper.getItemBorrowStatus(si.epc);
            enrichFromStockIn(item, si);
            allGroups.add(item);
        }

        applyFilter();
    }

    /**
     * Enrich a DisplayItem with searchable fields from its StockInInfo record.
     */
    private void enrichFromStockIn(DisplayItem item, StockInInfo si) {
        if (si == null) return;
        if (si.description != null) item.description = si.description;
        if (si.category != null) item.category = si.category;
        if (si.itemNumber != null) item.itemNumber = si.itemNumber;
        if (si.shelf != null) item.shelf = si.shelf;
        if (si.room != null) item.room = si.room;
    }

    // ==================== Filter ====================

    private void setFilter(int filter) {
        currentFilter = filter;
        Button[] btns = {btnFilterAll, btnFilterInStock, btnFilterBorrowed};
        for (int i = 0; i < btns.length; i++) {
            btns[i].setBackgroundResource(i == filter ? R.drawable.bg_btn_warehouse_primary : R.drawable.bg_btn_warehouse_gray);
        }
        applyFilter();
    }

    /**
     * Apply both search query and status filter to produce the filtered data set.
     */
    private void applyFilter() {
        filteredGroups.clear();
        filteredChildren.clear();
        flatFilteredItems.clear();

        // Pre-compute EPCs that match the search query via checkout logs
        Set<String> searchMatchEpcs = new HashSet<>();
        if (!searchQuery.isEmpty()) {
            List<CheckoutLogInfo> allLogs = dbHelper.getAllCheckoutLogs();
            for (CheckoutLogInfo log : allLogs) {
                boolean matchesStudent = false;
                if (log.studentId != null && log.studentId.toLowerCase().contains(searchQuery)) matchesStudent = true;
                if (log.studentName != null && log.studentName.toLowerCase().contains(searchQuery)) matchesStudent = true;
                if (matchesStudent) {
                    searchMatchEpcs.add(log.boxEpc);
                    if (log.checkedItems != null) {
                        for (String s : log.checkedItems.split(",\\s*")) {
                            if (!s.isEmpty()) searchMatchEpcs.add(s.trim());
                        }
                    }
                    if (log.missingItems != null) {
                        for (String s : log.missingItems.split(",\\s*")) {
                            if (!s.isEmpty()) searchMatchEpcs.add(s.trim());
                        }
                    }
                }
            }
        }

        for (DisplayItem group : allGroups) {
            boolean groupMatchesSearch = searchQuery.isEmpty() || matchesSearch(group, searchMatchEpcs);

            if (group.type == DisplayItem.TYPE_BOX) {
                List<DisplayItem> allKids = allChildren.getOrDefault(group.epc, new ArrayList<>());
                List<DisplayItem> fKids = new ArrayList<>();
                boolean anyChildMatch = false;

                for (DisplayItem c : allKids) {
                    boolean childMatchesSearch = searchQuery.isEmpty() || matchesSearch(c, searchMatchEpcs);
                    boolean childMatchesFilter = matchesFilter(c);
                    if (childMatchesSearch && childMatchesFilter) {
                        fKids.add(c);
                        anyChildMatch = true;
                    }
                }

                boolean groupMatchesFilter = matchesFilter(group);
                boolean include = (groupMatchesSearch && groupMatchesFilter) || anyChildMatch;
                if (currentFilter == FILTER_ALL && groupMatchesSearch) include = true;

                if (include) {
                    filteredGroups.add(group);
                    List<DisplayItem> kids = (searchQuery.isEmpty() && currentFilter == FILTER_ALL)
                            ? new ArrayList<>(allKids) : fKids;
                    filteredChildren.put(group.epc, kids);

                    // Add to flat list for grid view
                    flatFilteredItems.add(group);
                    flatFilteredItems.addAll(kids);
                }
            } else {
                // STANDALONE
                if (groupMatchesSearch && (matchesFilter(group) || currentFilter == FILTER_ALL)) {
                    filteredGroups.add(group);
                    flatFilteredItems.add(group);
                }
            }
        }

        updateStats();

        // Render the currently active view
        if (isGridViewMode) {
            renderGrid();
        } else {
            renderList();
        }
    }

    private boolean matchesFilter(DisplayItem item) {
        switch (currentFilter) {
            case FILTER_IN_STOCK: return "IN_STOCK".equals(item.borrowStatus);
            case FILTER_BORROWED: return "BORROWED".equals(item.borrowStatus);
            default: return true;
        }
    }

    private boolean matchesSearch(DisplayItem item, Set<String> logMatchEpcs) {
        if (searchQuery.isEmpty()) return true;
        String q = searchQuery;
        // Match name (shortId)
        if (item.name != null && item.name.toLowerCase().contains(q)) return true;
        // Match EPC
        if (item.epc != null && item.epc.toLowerCase().contains(q)) return true;
        // Match description
        if (item.description != null && item.description.toLowerCase().contains(q)) return true;
        // Match category
        if (item.category != null && item.category.toLowerCase().contains(q)) return true;
        // Match item number
        if (item.itemNumber != null && item.itemNumber.toLowerCase().contains(q)) return true;
        // Match shelf
        if (item.shelf != null && item.shelf.toLowerCase().contains(q)) return true;
        // Match room
        if (item.room != null && item.room.toLowerCase().contains(q)) return true;
        // Match photoPath
        if (item.photoPath != null && item.photoPath.toLowerCase().contains(q)) return true;
        // Match via checkout log associations
        if (logMatchEpcs.contains(item.epc)) return true;
        return false;
    }

    // ==================== Stats ====================

    private void updateStats() {
        int boxCount = 0, itemCount = 0, inStockCount = 0, borrowedCount = 0, standaloneCount = 0;

        for (DisplayItem g : allGroups) {
            if (g.type == DisplayItem.TYPE_BOX) {
                boxCount++;
                itemCount++;
                if ("BORROWED".equals(g.borrowStatus)) borrowedCount++;
                else inStockCount++;

                List<DisplayItem> kids = allChildren.getOrDefault(g.epc, new ArrayList<>());
                for (DisplayItem c : kids) {
                    itemCount++;
                    if ("BORROWED".equals(c.borrowStatus)) borrowedCount++;
                    else inStockCount++;
                }
            } else {
                standaloneCount++;
                itemCount++;
                if ("BORROWED".equals(g.borrowStatus)) borrowedCount++;
                else inStockCount++;
            }
        }

        int contentsCount = itemCount - boxCount - standaloneCount;

        tvStatItems.setText(getString(R.string.wh_stat_items, itemCount));
        tvStatBoxes.setText(getString(R.string.wh_stat_boxes, boxCount));
        tvStatContents.setText(getString(R.string.wh_stat_contents, contentsCount));
        tvStatStandalone.setText(getString(R.string.wh_stat_standalone, standaloneCount));
        tvStatInStock.setText(getString(R.string.wh_stat_in_stock, inStockCount));
        tvStatBorrowed.setText(getString(R.string.wh_stat_borrowed, borrowedCount));
    }

    // ==================== Export ====================

    /**
     * Export warehouse filtered data to Excel file.
     * Respects current search filter and status filter.
     */
    private void exportWarehouseData() {
        if (flatFilteredItems.isEmpty()) {
            mContext.showToast(R.string.wh_export_no_data);
            return;
        }

        // Build maps of EPC -> latest checkout log timestamp and borrower
        Map<String, String> latestLogMap = new HashMap<>();
        Map<String, String> latestBorrowerMap = new HashMap<>();
        List<CheckoutLogInfo> allLogs = dbHelper.getAllCheckoutLogs();
        for (CheckoutLogInfo log : allLogs) {
            if (!latestLogMap.containsKey(log.boxEpc)) {
                String dt = "";
                try {
                    dt = StringUtils.getTimeFormat(Long.parseLong(log.timestamp));
                } catch (Exception e) {
                    dt = log.timestamp;
                }
                latestLogMap.put(log.boxEpc, dt);
                // Record the latest borrower (student ID) for this EPC
                if (log.studentId != null && !log.studentId.isEmpty()) {
                    latestBorrowerMap.put(log.boxEpc, log.studentId);
                }
            }
        }

        String pathRoot = Environment.getExternalStorageDirectory() + File.separator + "UHF_exportData";
        File dir = new File(pathRoot);
        if (!dir.exists()) dir.mkdirs();
        String fileName = pathRoot + File.separator + "Warehouse_" + StringUtils.getTimeString() + ".xls";
        File file = new File(fileName);

        String[] header = {"物品名称", "TID", "物品类型", "所属箱子", "当前状态", "最近一次借还时间", "最近借出人"};
        ExcelUtils eu = new ExcelUtils();
        eu.createExcel(file, header);

        List<String[]> dataRows = new ArrayList<>();
        for (DisplayItem item : flatFilteredItems) {
            String typeLabel;
            String parentBox = "";
            switch (item.type) {
                case DisplayItem.TYPE_BOX:
                    typeLabel = "箱子";
                    break;
                case DisplayItem.TYPE_CONTENT:
                    typeLabel = "内容物";
                    parentBox = item.boxName != null ? item.boxName : "";
                    break;
                default:
                    typeLabel = "独立物品";
                    break;
            }
            String statusLabel = "BORROWED".equals(item.borrowStatus) ? "已借出" : "在库";
            String lastLog = latestLogMap.getOrDefault(item.epc, "");
            String lastBorrower = latestBorrowerMap.getOrDefault(item.epc, "");
            dataRows.add(new String[]{item.name, item.epc, typeLabel, parentBox, statusLabel, lastLog, lastBorrower});
        }

        eu.writeToExcel(dataRows);
        mContext.showToast(getString(R.string.wh_export_success, fileName));
    }

    // ==================== List View (ExpandableListView) ====================

    private void renderList() {
        // Hide grid view elements
        rvGridView.setVisibility(View.GONE);
        tvGridEmpty.setVisibility(View.GONE);
        // Show/hide list view elements
        if (filteredGroups.isEmpty()) {
            elvWhList.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            elvWhList.setVisibility(View.VISIBLE);
        }
        listAdapter.notifyDataSetChanged();
    }

    private class WarehouseListAdapter extends BaseExpandableListAdapter {
        @Override
        public int getGroupCount() { return filteredGroups.size(); }

        @Override
        public int getChildrenCount(int gp) {
            DisplayItem g = filteredGroups.get(gp);
            return g.type == DisplayItem.TYPE_BOX
                    ? filteredChildren.getOrDefault(g.epc, new ArrayList<>()).size()
                    : 0;
        }

        @Override
        public DisplayItem getGroup(int gp) { return filteredGroups.get(gp); }

        @Override
        public DisplayItem getChild(int gp, int cp) {
            return filteredChildren.getOrDefault(
                    filteredGroups.get(gp).epc, new ArrayList<>()).get(cp);
        }

        @Override public long getGroupId(int gp) { return gp; }
        @Override public long getChildId(int gp, int cp) { return cp; }
        @Override public boolean hasStableIds() { return false; }
        @Override public boolean isChildSelectable(int gp, int cp) { return true; }

        @Override
        public View getGroupView(int gp, boolean isExpanded, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = LayoutInflater.from(mContext).inflate(R.layout.item_br_group, parent, false);
            }
            final DisplayItem item = getGroup(gp);
            bindGroupView(cv, item);

            // Update expand/collapse arrow
            ImageView ivArrow = cv.findViewById(R.id.ivGroupArrow);
            if (item.type == DisplayItem.TYPE_BOX) {
                ivArrow.setVisibility(View.VISIBLE);
                ivArrow.setRotation(isExpanded ? 180f : 0f);
            } else {
                ivArrow.setVisibility(View.GONE);
            }

            // Click on the group item background (blank area): toggle expand/collapse for BOX, no-op for standalone
            cv.setOnClickListener(v -> {
                if (item.type == DisplayItem.TYPE_BOX) {
                    if (elvWhList.isGroupExpanded(gp)) {
                        elvWhList.collapseGroup(gp);
                    } else {
                        elvWhList.expandGroup(gp);
                    }
                }
            });

            return cv;
        }

        @Override
        public View getChildView(int gp, int cp, boolean isLast, View cv, ViewGroup parent) {
            if (cv == null) {
                cv = LayoutInflater.from(mContext).inflate(R.layout.item_br_child, parent, false);
            }
            bindChildView(cv, getChild(gp, cp));
            return cv;
        }
    }

    private void bindGroupView(View view, DisplayItem item) {
        TextView tvType = view.findViewById(R.id.tvGroupType);
        TextView tvName = view.findViewById(R.id.tvGroupName);
        TextView tvAudit = view.findViewById(R.id.tvAuditStatus);
        TextView tvBorrow = view.findViewById(R.id.tvBorrowStatus);
        TextView tvInfo = view.findViewById(R.id.tvGroupInfo);
        Button btnAction = view.findViewById(R.id.btnGroupBorrowReturn);

        btnAction.setVisibility(View.GONE);
        tvType.setText(item.type == DisplayItem.TYPE_BOX ? "\uD83D\uDCE6" : "\uD83D\uDCCB");

        tvName.setText(item.name);
        tvName.setTextColor(0xFF333333);
        final String detailEpc = item.epc;
        tvName.setOnClickListener(v -> mContext.openItemDetail(detailEpc));

        tvAudit.setVisibility(View.GONE);

        if ("BORROWED".equals(item.borrowStatus)) {
            tvBorrow.setText("已借出"); tvBorrow.setTextColor(0xFFFF5722);
        } else {
            tvBorrow.setText("在库"); tvBorrow.setTextColor(0xFF4CAF50);
        }

        if (item.type == DisplayItem.TYPE_BOX) {
            int childCount = filteredChildren.getOrDefault(item.epc, new ArrayList<>()).size();
            tvInfo.setText("TID: " + truncateEpc(item.epc)
                    + "  |  " + getString(R.string.warehouse_contents_count, childCount));
        } else {
            tvInfo.setText("TID: " + truncateEpc(item.epc));
        }
    }

    private void bindChildView(View view, DisplayItem item) {
        TextView tvName = view.findViewById(R.id.tvChildName);
        TextView tvEpc = view.findViewById(R.id.tvChildEpc);
        TextView tvAudit = view.findViewById(R.id.tvChildAudit);
        TextView tvBorrow = view.findViewById(R.id.tvChildBorrow);
        Button btnAction = view.findViewById(R.id.btnChildBorrowReturn);

        btnAction.setVisibility(View.GONE);

        tvName.setText(item.name);
        tvName.setTextColor(0xFF333333);
        final String detailEpc = item.epc;
        tvName.setOnClickListener(v -> mContext.openItemDetail(detailEpc));

        tvEpc.setText(truncateEpc(item.epc));
        tvAudit.setVisibility(View.GONE);

        if ("BORROWED".equals(item.borrowStatus)) {
            tvBorrow.setText("借出"); tvBorrow.setTextColor(0xFFFF5722);
        } else {
            tvBorrow.setText("在库"); tvBorrow.setTextColor(0xFF4CAF50);
        }
    }

    // ==================== Grid View Rendering ====================

    private void renderGrid() {
        // Hide list view elements
        elvWhList.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        // Show/hide grid view elements
        if (flatFilteredItems.isEmpty()) {
            tvGridEmpty.setVisibility(View.VISIBLE);
            rvGridView.setVisibility(View.GONE);
        } else {
            tvGridEmpty.setVisibility(View.GONE);
            rvGridView.setVisibility(View.VISIBLE);
        }
        gridAdapter.notifyDataSetChanged();
    }

    private class GridAdapter extends RecyclerView.Adapter<GridAdapter.GridVH> {
        @NonNull
        @Override
        public GridVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(mContext).inflate(R.layout.item_wh_grid_card, parent, false);
            return new GridVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GridVH holder, int position) {
            DisplayItem item = flatFilteredItems.get(position);

            // Name
            holder.tvName.setText(item.name);

            // Photo
            loadThumbnail(holder.ivPhoto, item.photoPath);

            // Borrow status badge
            if ("BORROWED".equals(item.borrowStatus)) {
                holder.tvStatus.setText("已借出");
                holder.tvStatus.setTextColor(0xFFFF5722);
            } else {
                holder.tvStatus.setText("在库");
                holder.tvStatus.setTextColor(0xFF4CAF50);
            }

            // Click to open detail
            holder.itemView.setOnClickListener(v -> mContext.openItemDetail(item.epc));
        }

        @Override
        public int getItemCount() {
            return flatFilteredItems.size();
        }

        class GridVH extends RecyclerView.ViewHolder {
            ImageView ivPhoto;
            TextView tvName, tvStatus;

            GridVH(@NonNull View itemView) {
                super(itemView);
                ivPhoto = itemView.findViewById(R.id.ivGridPhoto);
                tvName = itemView.findViewById(R.id.tvGridName);
                tvStatus = itemView.findViewById(R.id.tvGridStatus);
            }
        }
    }

    private void loadThumbnail(ImageView iv, String photoPath) {
        if (photoPath != null && !photoPath.isEmpty()) {
            File f = new File(photoPath);
            if (f.exists()) {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(photoPath, options);
                    int sampleSize = 1;
                    while (options.outWidth / sampleSize > 200) sampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = sampleSize;
                    Bitmap bmp = BitmapFactory.decodeFile(photoPath, options);
                    if (bmp != null) {
                        iv.setImageBitmap(bmp);
                        return;
                    }
                } catch (Exception e) {
                    // fall through to placeholder
                }
            }
        }
        // Default placeholder
        iv.setBackgroundColor(0xFFEEEEEE);
        iv.setImageDrawable(null);
    }

    private String truncateEpc(String epc) {
        if (epc == null) return "";
        return epc.length() > 12 ? epc.substring(0, 12) + "..." : epc;
    }

    // ==================== Delete ====================

    private void confirmDeleteBox(DisplayItem item) {
        new AlertDialog.Builder(mContext)
                .setMessage(R.string.warehouse_delete_box_confirm)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    dbHelper.deleteBox(item.epc);
                    dbHelper.deleteStockInsByEpc(item.epc);
                    mContext.showToast(R.string.warehouse_deleted);
                    loadData();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void myOnKeyDwon() {
        loadData();
    }
}
