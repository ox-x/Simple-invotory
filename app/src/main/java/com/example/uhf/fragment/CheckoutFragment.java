package com.example.uhf.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

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
import com.rscja.deviceapi.entity.InventoryParameter;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Borrow/Return Fragment (借还功能) - Refactored.
 * ExpandableListView with confirmation flow.
 */
public class CheckoutFragment extends KeyDwonFragment {

    private static final String TAG = "CheckoutFragment";
    private static final int FILTER_ALL = 0, FILTER_IN_STOCK = 1, FILTER_BORROWED = 2,
            FILTER_AUDITED = 3, FILTER_NOT_AUDITED = 4;

    private UHFMainActivity mContext;
    private DatabaseHelper dbHelper;

    // Student
    private EditText etStudentId;
    private Button btnConfirmStudent;
    private TextView tvStudentStatus;
    private String confirmedStudentId = "";

    // Scan
    private Button btnContinuousScan, btnStopScan;
    private TextView tvScanStatus, tvSummary;
    private boolean isScanning = false;

    // Power control
    private SeekBar seekBarPower;
    private TextView tvPower;

    // Filter
    private Button btnFilterAll, btnFilterInStock, btnFilterBorrowed, btnFilterAudited, btnFilterNotAudited;
    private int currentFilter = FILTER_ALL;

    // Bulk + Confirmation
    private Button btnBulkAll, btnCancelAll;
    private LinearLayout llConfirmBar;
    private TextView tvPendingCount;
    private Button btnConfirmAction, btnCancelAction;
    private List<DisplayItem> pendingItems = new ArrayList<>();

    // Expandable list
    private ExpandableListView elvItems;
    private ItemsAdapter adapter;

    // Data
    private List<DisplayItem> allGroups = new ArrayList<>();
    private Map<String, List<DisplayItem>> allChildren = new LinkedHashMap<>();
    private List<DisplayItem> filteredGroups = new ArrayList<>();
    private Map<String, List<DisplayItem>> filteredChildren = new LinkedHashMap<>();
    private boolean isDataLoaded = false;

    Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) processScannedTag((UHFTAGInfo) msg.obj);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_checkout, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        dbHelper = DatabaseHelper.getInstance(mContext);
        initViews();
        if (!isDataLoaded) {
            loadData();
            isDataLoaded = true;
        } else {
            // Restore visual state after view recreation on back from detail page
            restoreCheckoutState();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh current power level only, do NOT reload data to preserve state
        int power = mContext.mReader.getPower();
        if (power > 0 && power <= 30) {
            seekBarPower.setProgress(power - 1);
            tvPower.setText(getString(R.string.power_label, power));
        }
    }

    /**
     * Initialize power SeekBar with current power and set change listener.
     */
    private void initPowerSeekBar() {
        int power = mContext.mReader.getPower();
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
                if (mContext.mReader.setPower(powerValue)) {
                    android.util.Log.d(TAG, "Power set to " + powerValue + " dBm");
                } else {
                    android.util.Log.e(TAG, "Failed to set power to " + powerValue);
                }
            }
        });
    }

    private void initViews() {
        View v = getView();
        etStudentId = v.findViewById(R.id.etStudentId);
        btnConfirmStudent = v.findViewById(R.id.btnConfirmStudent);
        tvStudentStatus = v.findViewById(R.id.tvStudentStatus);
        btnContinuousScan = v.findViewById(R.id.btnContinuousScan);
        btnStopScan = v.findViewById(R.id.btnStopScan);
        tvScanStatus = v.findViewById(R.id.tvScanStatus);
        tvSummary = v.findViewById(R.id.tvSummary);
        btnFilterAll = v.findViewById(R.id.btnFilterAll);
        btnFilterInStock = v.findViewById(R.id.btnFilterInStock);
        btnFilterBorrowed = v.findViewById(R.id.btnFilterBorrowed);
        btnFilterAudited = v.findViewById(R.id.btnFilterAudited);
        btnFilterNotAudited = v.findViewById(R.id.btnFilterNotAudited);
        btnBulkAll = v.findViewById(R.id.btnBulkAll);
        btnCancelAll = v.findViewById(R.id.btnCancelAll);
        llConfirmBar = v.findViewById(R.id.llConfirmBar);
        tvPendingCount = v.findViewById(R.id.tvPendingCount);
        btnConfirmAction = v.findViewById(R.id.btnConfirmAction);
        btnCancelAction = v.findViewById(R.id.btnCancelAction);
        elvItems = v.findViewById(R.id.elvItems);

        // Power SeekBar
        seekBarPower = v.findViewById(R.id.seekBarCheckoutPower);
        tvPower = v.findViewById(R.id.tvCheckoutPower);
        initPowerSeekBar();

        adapter = new ItemsAdapter();
        elvItems.setAdapter(adapter);

        btnConfirmStudent.setOnClickListener(view -> confirmStudent());
        btnContinuousScan.setOnClickListener(view -> startContinuousScan());
        btnStopScan.setOnClickListener(view -> stopContinuousScan());
        btnBulkAll.setOnClickListener(view -> doBulkAll());
        btnCancelAll.setOnClickListener(view -> cancelAll());
        btnFilterAll.setOnClickListener(view -> setFilter(FILTER_ALL));
        btnFilterInStock.setOnClickListener(view -> setFilter(FILTER_IN_STOCK));
        btnFilterBorrowed.setOnClickListener(view -> setFilter(FILTER_BORROWED));
        btnFilterAudited.setOnClickListener(view -> setFilter(FILTER_AUDITED));
        btnFilterNotAudited.setOnClickListener(view -> setFilter(FILTER_NOT_AUDITED));
        btnConfirmAction.setOnClickListener(view -> confirmPending());
        btnCancelAction.setOnClickListener(view -> cancelPending());
    }

    // ==================== Data Loading ====================

    private void loadData() {
        allGroups.clear();
        allChildren.clear();
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
            group.totalContents = children.size() + 1; // +1 包含箱子本身
            allGroups.add(group);
            allChildren.put(box.epc, children);
        }
        List<StockInInfo> standalones = dbHelper.getStandaloneItems();
        for (StockInInfo si : standalones) {
            DisplayItem item = DisplayItem.fromStandalone(si);
            item.borrowStatus = dbHelper.getItemBorrowStatus(si.epc);
            allGroups.add(item);
        }
        applyFilter();
    }

    // ==================== Student (simplified) ====================

    private void confirmStudent() {
        String input = etStudentId.getText().toString().trim();
        if (input.isEmpty()) {
            mContext.showToast(R.string.checkout_student_required);
            return;
        }
        confirmedStudentId = input;
        tvStudentStatus.setText("ID: " + confirmedStudentId);
        tvStudentStatus.setTextColor(0xFF00E676);
    }

    // ==================== Scanning ====================

    private void doSingleScan() {
        if (confirmedStudentId.isEmpty()) { mContext.showToast(R.string.br_no_student); return; }
        UHFTAGInfo tag = mContext.mReader.inventorySingleTag();
        if (tag != null) {
            String tid = getTagId(tag);
            if (!TextUtils.isEmpty(tid)) {
                mContext.playSound(1);
                matchAndAudit(tid);
                tvScanStatus.setText(getString(R.string.checkout_tag_scanned, tid));
                tvScanStatus.setTextColor(0xFF00AA00);
            }
        } else {
            mContext.showToast(R.string.uhf_msg_inventory_fail);
        }
    }

    private void startContinuousScan() {
        if (confirmedStudentId.isEmpty()) { mContext.showToast(R.string.br_no_student); return; }
        mContext.mReader.setInventoryCallback(uhftagInfo -> {
            Message msg = handler.obtainMessage();
            msg.obj = uhftagInfo; msg.what = 1;
            handler.sendMessage(msg);
        });
        InventoryParameter param = new InventoryParameter();
        if (mContext.mReader.startInventoryTag(param)) {
            isScanning = true;
            btnContinuousScan.setEnabled(false);
            btnStopScan.setEnabled(true);
            mContext.loopFlag = true;
            tvScanStatus.setText(R.string.br_scan_status_scanning);
            tvScanStatus.setTextColor(0xFFFF5722);
        } else {
            mContext.showToast(R.string.uhf_msg_inventory_open_fail);
        }
    }

    private void stopContinuousScan() {
        if (isScanning) {
            mContext.mReader.stopInventory();
            mContext.mReader.setInventoryCallback(null);
            isScanning = false;
            mContext.loopFlag = false;
            btnContinuousScan.setEnabled(true);
            btnStopScan.setEnabled(false);
            tvScanStatus.setText(R.string.br_scan_status_ready);
            tvScanStatus.setTextColor(0xFF666666);
        }
    }

    private void processScannedTag(UHFTAGInfo info) {
        if (info == null) return;
        String tid = getTagId(info);
        if (TextUtils.isEmpty(tid)) return;
        tvScanStatus.setText(getString(R.string.checkout_tag_scanned, tid));
        tvScanStatus.setTextColor(0xFF00AA00);
        matchAndAudit(tid);
    }

    private void matchAndAudit(String epc) {
        boolean matched = false;
        for (DisplayItem group : allGroups) {
            if (group.epc.equalsIgnoreCase(epc) && !group.audited) {
                group.audited = true; matched = true; mContext.playSound(1);
                if (group.type == DisplayItem.TYPE_BOX) updateBoxAuditCount(group.epc);
                break;
            }
        }
        if (!matched) {
            for (Map.Entry<String, List<DisplayItem>> entry : allChildren.entrySet()) {
                for (DisplayItem child : entry.getValue()) {
                    if (child.epc.equalsIgnoreCase(epc) && !child.audited) {
                        child.audited = true; matched = true; mContext.playSound(1);
                        updateBoxAuditCount(entry.getKey()); break;
                    }
                }
                if (matched) break;
            }
        }
        if (!matched) {
            BoxInfo parentBox = dbHelper.getBoxForContent(epc);
            if (parentBox != null) {
                mContext.showToast(getString(R.string.content_belongs_to_box,
                        parentBox.shortId != null && !parentBox.shortId.isEmpty() ? parentBox.shortId : parentBox.epc));
            }
        }
        applyFilter();
    }

    private void updateBoxAuditCount(String boxEpc) {
        for (DisplayItem g : allGroups) {
            if (g.epc.equals(boxEpc)) {
                int count = 0;
                // 箱子本身已盘点则计入
                if (g.audited) count++;
                List<DisplayItem> kids = allChildren.get(boxEpc);
                if (kids != null) for (DisplayItem c : kids) if (c.audited) count++;
                g.auditedContents = count;
                break;
            }
        }
    }

    // ==================== Filter ====================

    private void restoreCheckoutState() {
        // Restore filter button colors
        Button[] btns = {btnFilterAll, btnFilterInStock, btnFilterBorrowed, btnFilterAudited, btnFilterNotAudited};
        for (int i = 0; i < btns.length; i++) btns[i].setBackgroundColor(i == currentFilter ? 0xFF3F51B5 : 0xFF9E9E9E);
        // Refresh filtered lists and summary
        applyFilter();
        // Restore confirm bar visibility and content
        updatePendingBar();
    }

    private void setFilter(int filter) {
        currentFilter = filter;
        Button[] btns = {btnFilterAll, btnFilterInStock, btnFilterBorrowed, btnFilterAudited, btnFilterNotAudited};
        for (int i = 0; i < btns.length; i++) btns[i].setBackgroundColor(i == filter ? 0xFF3F51B5 : 0xFF9E9E9E);
        applyFilter();
    }

    private void applyFilter() {
        filteredGroups.clear();
        filteredChildren.clear();
        for (DisplayItem group : allGroups) {
            boolean include = false;
            if (group.type == DisplayItem.TYPE_BOX) {
                List<DisplayItem> allKids = allChildren.getOrDefault(group.epc, new ArrayList<>());
                List<DisplayItem> fKids = new ArrayList<>();
                for (DisplayItem c : allKids) { if (matchesFilter(c)) { fKids.add(c); include = true; } }
                if (matchesFilter(group)) include = true;
                if (currentFilter == FILTER_ALL) include = true;
                if (include) {
                    filteredGroups.add(group);
                    filteredChildren.put(group.epc, currentFilter == FILTER_ALL ? new ArrayList<>(allKids) : fKids);
                }
            } else {
                if (matchesFilter(group) || currentFilter == FILTER_ALL) filteredGroups.add(group);
            }
        }
        updateSummary();
        adapter.notifyDataSetChanged();
    }

    private boolean matchesFilter(DisplayItem item) {
        switch (currentFilter) {
            case FILTER_IN_STOCK: return "IN_STOCK".equals(item.borrowStatus);
            case FILTER_BORROWED: return "BORROWED".equals(item.borrowStatus);
            case FILTER_AUDITED: return item.audited;
            case FILTER_NOT_AUDITED: return !item.audited;
            default: return true;
        }
    }

    private void updateSummary() {
        int total = 0, audited = 0;
        for (DisplayItem g : allGroups) {
            if (g.type == DisplayItem.TYPE_BOX) {
                // 箱子本身计入盘点
                total++;
                if (g.audited) audited++;
                List<DisplayItem> kids = allChildren.getOrDefault(g.epc, new ArrayList<>());
                total += kids.size();
                for (DisplayItem c : kids) if (c.audited) audited++;
            } else { total++; if (g.audited) audited++; }
        }
        tvSummary.setText(getString(R.string.br_items_summary, audited, total));
    }

    // ==================== Pending Confirmation Flow ====================

    private void addToPending(String action) {
        if (confirmedStudentId.isEmpty()) { mContext.showToast(R.string.br_no_student); return; }
        List<DisplayItem> candidates = new ArrayList<>();
        for (DisplayItem g : allGroups) {
            if (g.audited) candidates.add(g);
            if (g.type == DisplayItem.TYPE_BOX) {
                for (DisplayItem c : allChildren.getOrDefault(g.epc, new ArrayList<>())) {
                    if (c.audited) candidates.add(c);
                }
            }
        }
        if (candidates.isEmpty()) { mContext.showToast(R.string.br_no_items_audited); return; }

        pendingItems.clear();
        pendingItems.addAll(candidates);
        updatePendingBar();
        adapter.notifyDataSetChanged();
    }

    /**
     * Add a single item to pending (from item-level borrow/return button).
     */
    private void addSingleToPending(DisplayItem item) {
        if (confirmedStudentId.isEmpty()) { mContext.showToast(R.string.br_no_student); return; }
        if (!item.audited) return;

        if (!pendingItems.contains(item)) {
            pendingItems.add(item);
        }
        updatePendingBar();
        adapter.notifyDataSetChanged();
    }

    private void updatePendingBar() {
        if (pendingItems.isEmpty()) {
            llConfirmBar.setVisibility(View.GONE);
        } else {
            llConfirmBar.setVisibility(View.VISIBLE);
            int borrowCount = 0, returnCount = 0;
            for (DisplayItem item : pendingItems) {
                if ("IN_STOCK".equals(item.borrowStatus)) borrowCount++;
                else returnCount++;
            }
            StringBuilder sb = new StringBuilder();
            if (borrowCount > 0) sb.append("借出: ").append(borrowCount);
            if (returnCount > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append("归还: ").append(returnCount);
            }
            sb.append("  ").append(getString(R.string.br_filter_audited));
            tvPendingCount.setText(sb.toString());
            // Confirm button color: orange-red if any borrow, green if only returns
            btnConfirmAction.setBackgroundColor(borrowCount > 0 ? 0xFFFF5722 : 0xFF4CAF50);
        }
    }

    /**
     * 全部借还：将所有已盘点（audited=true）且尚未在待选列表中的物品加入 pendingItems，
     * 显示底部确认栏让用户统一确认执行。
     */
    private void doBulkAll() {
        if (confirmedStudentId.isEmpty()) { mContext.showToast(R.string.br_no_student); return; }

        boolean added = false;
        // 收集箱子
        for (DisplayItem g : allGroups) {
            if (g.audited && !pendingItems.contains(g)) {
                pendingItems.add(g);
                added = true;
            }
            // 收集内容物
            if (g.type == DisplayItem.TYPE_BOX) {
                for (DisplayItem c : allChildren.getOrDefault(g.epc, new ArrayList<>())) {
                    if (c.audited && !pendingItems.contains(c)) {
                        pendingItems.add(c);
                        added = true;
                    }
                }
            }
        }

        if (!added) {
            mContext.showToast(R.string.br_no_items_audited);
            return;
        }

        updatePendingBar();
        adapter.notifyDataSetChanged();
    }

    private void confirmPending() {
        if (pendingItems.isEmpty()) return;
        int borrowCount = 0, returnCount = 0;
        for (DisplayItem item : pendingItems) {
            String itemAction = "IN_STOCK".equals(item.borrowStatus) ? "BORROW" : "RETURN";
            executeLogItem(item, itemAction);
            if ("BORROW".equals(itemAction)) borrowCount++;
            else returnCount++;
        }
        pendingItems.clear();
        updatePendingBar();
        loadData();
        StringBuilder msg = new StringBuilder();
        if (borrowCount > 0) msg.append(getString(R.string.br_bulk_borrow_done, borrowCount));
        if (returnCount > 0) {
            if (msg.length() > 0) msg.append(" ");
            msg.append(getString(R.string.br_bulk_return_done, returnCount));
        }
        mContext.showToast(msg.toString());
        // 借还完成后自动导出日志
        exportLogs();
        // 重置学生ID状态，要求下次操作重新输入
        confirmedStudentId = "";
        etStudentId.setText("");
        tvStudentStatus.setText("请输入学生ID");
        tvStudentStatus.setTextColor(0xCCFFFFFF);
    }

    private void cancelPending() {
        // 清除待选列表
        pendingItems.clear();

        // 重置筛选到全部
        currentFilter = FILTER_ALL;
        Button[] btns = {btnFilterAll, btnFilterInStock, btnFilterBorrowed, btnFilterAudited, btnFilterNotAudited};
        for (int i = 0; i < btns.length; i++) btns[i].setBackgroundColor(i == FILTER_ALL ? 0xFF3F51B5 : 0xFF9E9E9E);

        updatePendingBar();
        applyFilter();
    }

    /**
     * 全部取消：彻底重置所有状态，包括待选列表、盘点状态、筛选器和学生ID。
     */
    private void cancelAll() {
        // 1. 清空待确认列表
        pendingItems.clear();

        // 2. 重置所有物品的盘点状态
        for (DisplayItem g : allGroups) {
            g.audited = false;
            if (g.type == DisplayItem.TYPE_BOX) {
                g.auditedContents = 0;
            }
        }
        for (List<DisplayItem> children : allChildren.values()) {
            for (DisplayItem c : children) {
                c.audited = false;
            }
        }

        // 3. 重置筛选器到"全部"
        currentFilter = FILTER_ALL;
        Button[] btns = {btnFilterAll, btnFilterInStock, btnFilterBorrowed, btnFilterAudited, btnFilterNotAudited};
        for (int i = 0; i < btns.length; i++) btns[i].setBackgroundColor(i == FILTER_ALL ? 0xFF3F51B5 : 0xFF9E9E9E);

        // 4. 清空已确认的学生ID
        confirmedStudentId = "";
        etStudentId.setText("");
        tvStudentStatus.setText("");
        tvStudentStatus.setTextColor(0xFF9E9E9E);

        // 5. 更新界面状态
        updatePendingBar();
        applyFilter();
    }

    private void executeLogItem(DisplayItem item, String action) {
        String itemType;
        String parentBox = "";
        switch (item.type) {
            case DisplayItem.TYPE_BOX: itemType = "BOX"; break;
            case DisplayItem.TYPE_STANDALONE: itemType = "STANDALONE"; break;
            default: itemType = "CONTENT"; parentBox = item.boxName; break;
        }
        dbHelper.insertCheckoutLog(confirmedStudentId, item.epc, item.name, action, itemType, parentBox);
        item.borrowStatus = "BORROW".equals(action) ? "BORROWED" : "IN_STOCK";
        item.audited = false;
    }

    // ==================== Export ====================

    private void exportLogs() {
        List<CheckoutLogInfo> logs = dbHelper.getAllCheckoutLogs();
        if (logs.isEmpty()) { mContext.showToast(R.string.export_no_data); return; }
        String pathRoot = android.os.Environment.getExternalStorageDirectory() + File.separator + "UHF_exportData";
        File dir = new File(pathRoot);
        if (!dir.exists()) dir.mkdirs();
        String fileName = pathRoot + File.separator + "CheckoutLog_" + StringUtils.getTimeString() + ".xls";
        File file = new File(fileName);
        String[] header = {"Date/Time", "Student ID", "TID", "物品名称", "物品类型", "所属箱子", "操作"};
        ExcelUtils eu = new ExcelUtils();
        eu.createExcel(file, header);
        List<String[]> data = new ArrayList<>();
        for (CheckoutLogInfo log : logs) {
            String dt = "";
            try { dt = StringUtils.getTimeFormat(Long.parseLong(log.timestamp)); } catch (Exception e) { dt = log.timestamp; }
            String typeLabel = "";
            if ("BOX".equals(log.itemType)) typeLabel = "箱子";
            else if ("STANDALONE".equals(log.itemType)) typeLabel = "独立物件";
            else if ("CONTENT".equals(log.itemType)) typeLabel = "内容物";
            data.add(new String[]{dt, log.studentId, log.boxEpc, log.boxShortId, typeLabel, log.parentBoxName, log.status});
        }
        eu.writeToExcel(data);
        mContext.showToast(getString(R.string.export_succ, fileName));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isScanning) stopContinuousScan();
    }

    @Override
    public void myOnKeyDwon() { doSingleScan(); }

    /**
     * 多功能按键（key code 142）：循环扫描切换开关。
     */
    @Override
    public void onMultiFunctionKey() {
        if (isScanning) {
            stopContinuousScan();
        } else {
            startContinuousScan();
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

    /**
     * Reset pending state before navigating to item detail page.
     * Clears pending queue, hides confirm bar, and refreshes list highlights.
     */
    private void resetPendingState() {
        pendingItems.clear();
        updatePendingBar();
        adapter.notifyDataSetChanged();
    }

    // ==================== ExpandableListAdapter ====================

    private class ItemsAdapter extends BaseExpandableListAdapter {
        @Override public int getGroupCount() { return filteredGroups.size(); }
        @Override public int getChildrenCount(int gp) {
            DisplayItem g = filteredGroups.get(gp);
            return g.type == DisplayItem.TYPE_BOX ? filteredChildren.getOrDefault(g.epc, new ArrayList<>()).size() : 0;
        }
        @Override public DisplayItem getGroup(int gp) { return filteredGroups.get(gp); }
        @Override public DisplayItem getChild(int gp, int cp) {
            return filteredChildren.getOrDefault(filteredGroups.get(gp).epc, new ArrayList<>()).get(cp);
        }
        @Override public long getGroupId(int gp) { return gp; }
        @Override public long getChildId(int gp, int cp) { return cp; }
        @Override public boolean hasStableIds() { return false; }
        @Override public boolean isChildSelectable(int gp, int cp) { return true; }

        @Override
        public View getGroupView(int gp, boolean isExpanded, View cv, ViewGroup parent) {
            ViewHolder h;
            if (cv == null) {
                cv = LayoutInflater.from(mContext).inflate(R.layout.item_br_group, parent, false);
                h = new ViewHolder();
                h.tvType = cv.findViewById(R.id.tvGroupType);
                h.tvName = cv.findViewById(R.id.tvGroupName);
                h.tvInfo = cv.findViewById(R.id.tvGroupInfo);
                h.tvAudit = cv.findViewById(R.id.tvAuditStatus);
                h.tvBorrow = cv.findViewById(R.id.tvBorrowStatus);
                h.btnAction = cv.findViewById(R.id.btnGroupBorrowReturn);
                cv.setTag(h);
            } else { h = (ViewHolder) cv.getTag(); }
            bindGroup(h, getGroup(gp), isExpanded);
            return cv;
        }

        @Override
        public View getChildView(int gp, int cp, boolean isLast, View cv, ViewGroup parent) {
            ChildViewHolder h;
            if (cv == null) {
                cv = LayoutInflater.from(mContext).inflate(R.layout.item_br_child, parent, false);
                h = new ChildViewHolder();
                h.tvName = cv.findViewById(R.id.tvChildName);
                h.tvEpc = cv.findViewById(R.id.tvChildEpc);
                h.tvAudit = cv.findViewById(R.id.tvChildAudit);
                h.tvBorrow = cv.findViewById(R.id.tvChildBorrow);
                h.btnAction = cv.findViewById(R.id.btnChildBorrowReturn);
                cv.setTag(h);
            } else { h = (ChildViewHolder) cv.getTag(); }
            bindChild(h, getChild(gp, cp));
            return cv;
        }

        private void bindGroup(ViewHolder h, DisplayItem item, boolean isExpanded) {
            h.tvType.setText(item.type == DisplayItem.TYPE_BOX ? "📦" : "📋");
            h.tvName.setText(item.name);
            h.tvInfo.setText(item.type == DisplayItem.TYPE_BOX
                    ? getString(R.string.br_items_summary, item.auditedContents, item.totalContents)
                    : "TID: " + item.epc);

            if (item.audited) {
                h.tvAudit.setText("✓已盘点"); h.tvAudit.setTextColor(0xFF4CAF50);
            } else {
                h.tvAudit.setText("✗未盘点"); h.tvAudit.setTextColor(0xFF9E9E9E);
            }
            if ("BORROWED".equals(item.borrowStatus)) {
                h.tvBorrow.setText("已借出"); h.tvBorrow.setTextColor(0xFFFF5722);
            } else {
                h.tvBorrow.setText("在库"); h.tvBorrow.setTextColor(0xFF4CAF50);
            }

            // Highlight if in pending list
            boolean isPending = pendingItems.contains(item);
            if (isPending) {
                h.tvName.setTextColor(0xFFFF5722);
            } else {
                h.tvName.setTextColor(0xFF333333);
            }

            // Click name to open item detail
            final String detailEpc = item.epc;
            h.tvName.setOnClickListener(v -> mContext.openItemDetail(detailEpc));

            if (item.audited) {
                h.btnAction.setEnabled(true);
                boolean willBorrow = "IN_STOCK".equals(item.borrowStatus);
                h.btnAction.setText(willBorrow ? "借" : "还");
                h.btnAction.setBackgroundColor(willBorrow ? 0xFFFF5722 : 0xFF4CAF50);
                h.btnAction.setOnClickListener(v -> addSingleToPending(item));
            } else {
                h.btnAction.setEnabled(false);
                h.btnAction.setText("借");
                h.btnAction.setBackgroundColor(0xFF9E9E9E);
                h.btnAction.setOnClickListener(null);
            }
        }

        private void bindChild(ChildViewHolder h, DisplayItem item) {
            h.tvName.setText(item.name);
            h.tvEpc.setText(item.epc.length() > 12 ? item.epc.substring(0, 12) + "..." : item.epc);
            if (item.audited) {
                h.tvAudit.setText("✓已盘点"); h.tvAudit.setTextColor(0xFF4CAF50);
            } else {
                h.tvAudit.setText("✗未盘点"); h.tvAudit.setTextColor(0xFF9E9E9E);
            }
            if ("BORROWED".equals(item.borrowStatus)) {
                h.tvBorrow.setText("借出"); h.tvBorrow.setTextColor(0xFFFF5722);
            } else {
                h.tvBorrow.setText("在库"); h.tvBorrow.setTextColor(0xFF4CAF50);
            }

            boolean isPending = pendingItems.contains(item);
            h.tvName.setTextColor(isPending ? 0xFFFF5722 : 0xFF333333);

            // Click name to open item detail
            final String detailEpc = item.epc;
            h.tvName.setOnClickListener(v -> mContext.openItemDetail(detailEpc));

            if (item.audited) {
                h.btnAction.setEnabled(true);
                boolean willBorrow = "IN_STOCK".equals(item.borrowStatus);
                h.btnAction.setText(willBorrow ? "借" : "还");
                h.btnAction.setBackgroundColor(willBorrow ? 0xFFFF5722 : 0xFF4CAF50);
                h.btnAction.setOnClickListener(v -> addSingleToPending(item));
            } else {
                h.btnAction.setEnabled(false);
                h.btnAction.setText("借");
                h.btnAction.setBackgroundColor(0xFF9E9E9E);
                h.btnAction.setOnClickListener(null);
            }
        }

        class ViewHolder { TextView tvType, tvName, tvInfo, tvAudit, tvBorrow; Button btnAction; }
        class ChildViewHolder { TextView tvName, tvEpc, tvAudit, tvBorrow; Button btnAction; }
    }
}
