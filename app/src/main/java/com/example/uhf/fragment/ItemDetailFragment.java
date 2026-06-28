package com.example.uhf.fragment;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.db.BoxInfo;
import com.example.uhf.db.CheckoutLogInfo;
import com.example.uhf.db.ContentInfo;
import com.example.uhf.db.DatabaseHelper;
import com.example.uhf.db.StockInInfo;
import com.example.uhf.view.CircleSeekBar;
import com.example.uhf.view.RadarView;
import com.rscja.deviceapi.entity.RadarLocationEntity;
import com.rscja.deviceapi.interfaces.IUHF;
import com.rscja.deviceapi.interfaces.IUHFRadarLocationCallback;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Item Detail Fragment (物品详情页).
 * Shows all information about a single item: basic info, box info, multi-photo,
 * borrow status, creation time, and full history (stock-in + checkout logs).
 * Edit mode is only available when opened from StockInFragment (fromStockIn = true).
 */
public class ItemDetailFragment extends KeyDwonFragment {

    private static final String ARG_EPC = "arg_epc";
    private static final String ARG_FROM_STOCK_IN = "arg_from_stock_in";

    private static final String TAG = "ItemDetailFragment";

    private UHFMainActivity mContext;
    private DatabaseHelper dbHelper;

    private String itemEpc = "";
    private boolean fromStockIn = false;

    // Views
    private Button btnBack;
    private Button btnEdit;
    private TextView tvTitle;

    // Multi-photo views
    private FrameLayout flPhotoContainer;
    private ViewPager vpPhotos;
    private TextView tvPhotoIndicator;
    private List<String> photoPaths = new ArrayList<>();

    // Basic info views
    private TextView tvEpc, tvShortId, tvDesc, tvType;
    private TextView tvCategory, tvItemNumber, tvShelf, tvRoom;
    private LinearLayout llBoxInfo;
    private TextView tvBoxName, tvBoxEpc;
    private TextView tvBorrowStatus, tvCreatedAt;
    private TextView tvHistoryEmpty;
    private LinearLayout llHistoryList;

    // Edit fields
    private LinearLayout llEditFields;
    private EditText etDetailShortId, etDetailDesc, etDetailCategory, etDetailItemNumber;
    private EditText etDetailShelf, etDetailRoom;
    private Button btnDetailSave, btnDetailCancel;
    private boolean isEditMode = false;

    // Radar views
    private RadarView radarView;
    private CircleSeekBar seekBarPower;
    private Button btnRadarStart;
    private Button btnRadarStop;
    private boolean inventoryFlag = false;
    private int progress = 5;

    // History collapse/expand
    private LinearLayout llHistoryHeader;
    private ImageView ivHistoryArrow;
    private boolean isHistoryExpanded = true;

    // Data
    private List<Object> historyList = new ArrayList<>();
    private StockInInfo currentStockInInfo = null;

    public static ItemDetailFragment newInstance(String epc) {
        return newInstance(epc, false);
    }

    public static ItemDetailFragment newInstance(String epc, boolean fromStockIn) {
        ItemDetailFragment f = new ItemDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EPC, epc);
        args.putBoolean(ARG_FROM_STOCK_IN, fromStockIn);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            itemEpc = getArguments().getString(ARG_EPC, "");
            fromStockIn = getArguments().getBoolean(ARG_FROM_STOCK_IN, false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_item_detail, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        dbHelper = DatabaseHelper.getInstance(mContext);
        initViews();
        loadItemDetail();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadItemDetail();
    }

    private void initViews() {
        View v = getView();
        btnBack = v.findViewById(R.id.btnDetailBack);
        btnEdit = v.findViewById(R.id.btnDetailEdit);
        tvTitle = v.findViewById(R.id.tvDetailTitle);

        // Multi-photo
        flPhotoContainer = v.findViewById(R.id.flDetailPhotoContainer);
        vpPhotos = v.findViewById(R.id.vpDetailPhotos);
        tvPhotoIndicator = v.findViewById(R.id.tvDetailPhotoIndicator);

        // Basic info
        tvEpc = v.findViewById(R.id.tvDetailEpc);
        tvShortId = v.findViewById(R.id.tvDetailShortId);
        tvDesc = v.findViewById(R.id.tvDetailDesc);
        tvType = v.findViewById(R.id.tvDetailType);
        tvCategory = v.findViewById(R.id.tvDetailCategory);
        tvItemNumber = v.findViewById(R.id.tvDetailItemNumber);
        tvShelf = v.findViewById(R.id.tvDetailShelf);
        tvRoom = v.findViewById(R.id.tvDetailRoom);
        llBoxInfo = v.findViewById(R.id.llDetailBoxInfo);
        tvBoxName = v.findViewById(R.id.tvDetailBoxName);
        tvBoxEpc = v.findViewById(R.id.tvDetailBoxEpc);
        tvBorrowStatus = v.findViewById(R.id.tvDetailBorrowStatus);
        tvCreatedAt = v.findViewById(R.id.tvDetailCreatedAt);
        tvHistoryEmpty = v.findViewById(R.id.tvDetailHistoryEmpty);
        llHistoryList = v.findViewById(R.id.llDetailHistoryList);

        // Edit fields
        llEditFields = v.findViewById(R.id.llDetailEditFields);
        etDetailShortId = v.findViewById(R.id.etDetailShortId);
        etDetailDesc = v.findViewById(R.id.etDetailDesc);
        etDetailCategory = v.findViewById(R.id.etDetailCategory);
        etDetailItemNumber = v.findViewById(R.id.etDetailItemNumber);
        etDetailShelf = v.findViewById(R.id.etDetailShelf);
        etDetailRoom = v.findViewById(R.id.etDetailRoom);
        btnDetailSave = v.findViewById(R.id.btnDetailSave);
        btnDetailCancel = v.findViewById(R.id.btnDetailCancel);

        // Radar views
        radarView = v.findViewById(R.id.radarViewDetail);
        seekBarPower = v.findViewById(R.id.seekBarPowerDetail);
        btnRadarStart = v.findViewById(R.id.btnRadarStart);
        btnRadarStop = v.findViewById(R.id.btnRadarStop);
        seekBarPower.setEnabled(false);
        seekBarPower.setProgress(5);

        // History collapse/expand views
        llHistoryHeader = v.findViewById(R.id.llHistoryHeader);
        ivHistoryArrow = v.findViewById(R.id.ivHistoryArrow);

        btnBack.setOnClickListener(vv -> goBack());
        btnEdit.setOnClickListener(vv -> toggleEditMode());
        btnDetailSave.setOnClickListener(vv -> saveChanges());
        btnDetailCancel.setOnClickListener(vv -> exitEditMode());

        // Radar button listeners
        btnRadarStart.setOnClickListener(vv -> startLocated());
        btnRadarStop.setOnClickListener(vv -> stopLocated());

        // History header click listener
        llHistoryHeader.setOnClickListener(vv -> toggleHistoryVisibility());

        // Edit button visibility: only show when fromStockIn
        if (!fromStockIn) {
            btnEdit.setVisibility(View.GONE);
            llEditFields.setVisibility(View.GONE);
        }
    }

    private void goBack() {
        if (getFragmentManager() != null) {
            getFragmentManager().popBackStack();
        }
    }

    private void loadItemDetail() {
        if (itemEpc.isEmpty()) return;

        // Try to find as Box
        BoxInfo box = dbHelper.getBoxByEpc(itemEpc);
        if (box != null) {
            fillBoxInfo(box);
            return;
        }

        // Try to find as Content
        BoxInfo parentBox = dbHelper.getBoxForContent(itemEpc);
        if (parentBox != null) {
            List<ContentInfo> contents = dbHelper.getContentsByBoxEpc(parentBox.epc);
            for (ContentInfo c : contents) {
                if (c.epc.equalsIgnoreCase(itemEpc)) {
                    fillContentInfo(c, parentBox);
                    return;
                }
            }
        }

        // Try to find as Standalone (from stock_ins)
        StockInInfo si = dbHelper.getLatestStockIn(itemEpc);
        if (si != null && "STANDALONE".equals(si.type)) {
            fillStandaloneInfo(si);
            return;
        }

        // Fallback
        fillFallbackInfo();
    }

    private void fillBoxInfo(BoxInfo box) {
        tvTitle.setText(box.toString());
        tvEpc.setText("TID: " + na(box.epc));
        tvShortId.setText("简称: " + na(box.shortId));
        tvDesc.setText("描述: " + na(box.description));
        tvType.setText("类型: 箱子 (BOX)");
        llBoxInfo.setVisibility(View.GONE);

        setBorrowStatusText(dbHelper.getItemBorrowStatus(box.epc));
        setCreatedAt(box.createdAt);

        // Photos from stock_in record (which may have multiple)
        StockInInfo si = dbHelper.getLatestStockIn(itemEpc);
        List<String> paths = new ArrayList<>();
        if (si != null && si.photoPaths != null) {
            for (String p : si.photoPaths) {
                if (p != null && !p.isEmpty() && new File(p).exists()) paths.add(p);
            }
        }
        // Fallback to box photo if no stock_in photos
        if (paths.isEmpty() && box.photoPath != null && !box.photoPath.isEmpty()
                && new File(box.photoPath).exists()) {
            paths.add(box.photoPath);
        }
        loadPhotos(paths);

        currentStockInInfo = si;
        updateEditButtonVisibility();
        fillStockInFields(si);
        loadHistory(itemEpc);
    }

    private void fillContentInfo(ContentInfo content, BoxInfo parentBox) {
        tvTitle.setText(content.toString());
        tvEpc.setText("TID: " + na(content.epc));
        tvShortId.setText("简称: " + na(content.shortId));
        tvDesc.setText("描述: " + na(content.description));
        tvType.setText("类型: 内容物 (CONTENT)");

        llBoxInfo.setVisibility(View.VISIBLE);
        tvBoxName.setText("箱子: " + parentBox.toString());
        tvBoxEpc.setText("箱子 TID: " + parentBox.epc);

        setBorrowStatusText(dbHelper.getItemBorrowStatus(content.epc));
        setCreatedAt(content.createdAt);

        StockInInfo si = dbHelper.getLatestStockIn(itemEpc);
        List<String> paths = new ArrayList<>();
        if (si != null && si.photoPaths != null) {
            for (String p : si.photoPaths) {
                if (p != null && !p.isEmpty() && new File(p).exists()) paths.add(p);
            }
        }
        if (paths.isEmpty() && content.photoPath != null && !content.photoPath.isEmpty()
                && new File(content.photoPath).exists()) {
            paths.add(content.photoPath);
        }
        loadPhotos(paths);

        currentStockInInfo = si;
        updateEditButtonVisibility();
        fillStockInFields(si);
        loadHistory(content.epc);
    }

    private void fillStandaloneInfo(StockInInfo si) {
        tvTitle.setText(si.toString());
        tvEpc.setText("TID: " + na(si.epc));
        tvShortId.setText("简称: " + na(si.shortId));
        tvDesc.setText("描述: " + na(si.description));
        tvType.setText("类型: 独立物件 (STANDALONE)");
        llBoxInfo.setVisibility(View.GONE);

        setBorrowStatusText(dbHelper.getItemBorrowStatus(si.epc));
        setCreatedAt(si.timestamp);

        List<String> paths = new ArrayList<>();
        if (si.photoPaths != null) {
            for (String p : si.photoPaths) {
                if (p != null && !p.isEmpty() && new File(p).exists()) paths.add(p);
            }
        }
        loadPhotos(paths);

        currentStockInInfo = si;
        updateEditButtonVisibility();
        fillStockInFields(si);
        loadHistory(si.epc);
    }

    private void fillFallbackInfo() {
        tvTitle.setText("物品详情");
        tvEpc.setText("TID: " + itemEpc);
        tvShortId.setText("简称: N/A");
        tvDesc.setText("描述: N/A");
        tvType.setText("类型: 未知");
        llBoxInfo.setVisibility(View.GONE);

        setBorrowStatusText(dbHelper.getItemBorrowStatus(itemEpc));

        StockInInfo si = dbHelper.getLatestStockIn(itemEpc);
        if (si != null) {
            setCreatedAt(si.timestamp);
            List<String> paths = new ArrayList<>();
            if (si.photoPaths != null) {
                for (String p : si.photoPaths) {
                    if (p != null && !p.isEmpty() && new File(p).exists()) paths.add(p);
                }
            }
            loadPhotos(paths);
            currentStockInInfo = si;
            fillStockInFields(si);
        } else {
            flPhotoContainer.setVisibility(View.GONE);
            tvCategory.setText("种类: N/A");
            tvItemNumber.setText("货号: N/A");
            tvShelf.setText("货架: N/A");
            tvRoom.setText("房间: N/A");
        }
        updateEditButtonVisibility();
        loadHistory(itemEpc);
    }

    /**
     * Fill category/itemNumber/shelf/room from stock_in record.
     */
    private void fillStockInFields(StockInInfo si) {
        if (si != null) {
            tvCategory.setText("种类: " + na(si.category));
            tvItemNumber.setText("货号: " + na(si.itemNumber));
            tvShelf.setText("货架: " + na(si.shelf));
            tvRoom.setText("房间: " + na(si.room));
        } else {
            tvCategory.setText("种类: N/A");
            tvItemNumber.setText("货号: N/A");
            tvShelf.setText("货架: N/A");
            tvRoom.setText("房间: N/A");
        }
    }

    private String na(String value) {
        return (value != null && !value.isEmpty()) ? value : "N/A";
    }

    /**
     * Control edit button visibility based on fromStockIn flag and record existence.
     */
    private void updateEditButtonVisibility() {
        if (fromStockIn && currentStockInInfo != null) {
            btnEdit.setVisibility(View.VISIBLE);
        } else {
            btnEdit.setVisibility(View.GONE);
        }
    }

    // ==================== Multi-Photo ViewPager ====================

    private void loadPhotos(List<String> paths) {
        photoPaths.clear();
        if (paths != null) {
            for (String p : paths) {
                if (p != null && !p.isEmpty() && new File(p).exists()) {
                    photoPaths.add(p);
                }
            }
        }

        if (photoPaths.isEmpty()) {
            flPhotoContainer.setVisibility(View.GONE);
            return;
        }

        flPhotoContainer.setVisibility(View.VISIBLE);
        vpPhotos.setAdapter(new PhotoPagerAdapter());

        if (photoPaths.size() > 1) {
            tvPhotoIndicator.setVisibility(View.VISIBLE);
            tvPhotoIndicator.setText("1/" + photoPaths.size());
            vpPhotos.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    tvPhotoIndicator.setText((position + 1) + "/" + photoPaths.size());
                }
            });
        } else {
            tvPhotoIndicator.setVisibility(View.GONE);
        }
    }

    private class PhotoPagerAdapter extends PagerAdapter {
        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            ImageView iv = new ImageView(mContext);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFFEEEEEE);

            String path = photoPaths.get(position);
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, options);
                int sampleSize = 1;
                while (options.outWidth / sampleSize > 800) sampleSize *= 2;
                options.inJustDecodeBounds = false;
                options.inSampleSize = sampleSize;
                Bitmap bmp = BitmapFactory.decodeFile(path, options);
                if (bmp != null) iv.setImageBitmap(bmp);
            } catch (Exception e) {
                Log.e(TAG, "Error loading photo: " + path, e);
            }

            // Click to view full-size image
            iv.setOnClickListener(v -> showFullScreenPhoto(path));

            container.addView(iv, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            return iv;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            return photoPaths.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    /**
     * Show a full-screen dialog to view the photo at full resolution.
     */
    private void showFullScreenPhoto(String path) {
        Dialog dialog = new Dialog(mContext, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Create a full-screen ImageView with black background
        ImageView fullImageView = new ImageView(mContext);
        fullImageView.setBackgroundColor(Color.BLACK);
        fullImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // Load full resolution image
        try {
            Bitmap bmp = BitmapFactory.decodeFile(path);
            if (bmp != null) fullImageView.setImageBitmap(bmp);
        } catch (Exception e) {
            Log.e(TAG, "Error loading full photo: " + path, e);
        }

        // Close hint text at the top
        LinearLayout layout = new LinearLayout(mContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK);
        layout.setGravity(Gravity.CENTER);

        TextView tvHint = new TextView(mContext);
        tvHint.setText("点击任意位置关闭");
        tvHint.setTextColor(0xAAFFFFFF);
        tvHint.setTextSize(13);
        tvHint.setGravity(Gravity.CENTER);
        tvHint.setPadding(0, 16, 0, 8);

        layout.addView(tvHint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fullImageView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(layout);

        // Close on tap anywhere
        layout.setOnClickListener(v -> dialog.dismiss());
        fullImageView.setOnClickListener(v -> dialog.dismiss());

        // Make dialog fill the screen
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }

        dialog.show();
    }

    // ==================== Status helpers ====================

    private void setBorrowStatusText(String status) {
        if ("BORROWED".equals(status)) {
            tvBorrowStatus.setText("借还状态: 已借出");
            tvBorrowStatus.setTextColor(0xFFFF5722);
        } else {
            tvBorrowStatus.setText("借还状态: 在库");
            tvBorrowStatus.setTextColor(0xFF4CAF50);
        }
    }

    private void setCreatedAt(String timestamp) {
        if (timestamp != null && !timestamp.isEmpty()) {
            try {
                long ts = Long.parseLong(timestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                tvCreatedAt.setText("入库时间: " + sdf.format(new java.util.Date(ts)));
            } catch (Exception e) {
                tvCreatedAt.setText("入库时间: " + timestamp);
            }
        } else {
            tvCreatedAt.setText("入库时间: N/A");
        }
    }

    // ==================== History ====================

    private void loadHistory(String epc) {
        historyList.clear();
        historyList.addAll(dbHelper.getStockInHistory(epc));
        historyList.addAll(dbHelper.getCheckoutHistoryForItem(epc));

        Collections.sort(historyList, (a, b) -> {
            String tsA = (a instanceof StockInInfo) ? ((StockInInfo) a).timestamp : ((CheckoutLogInfo) a).timestamp;
            String tsB = (b instanceof StockInInfo) ? ((StockInInfo) b).timestamp : ((CheckoutLogInfo) b).timestamp;
            try {
                return Long.compare(Long.parseLong(tsB), Long.parseLong(tsA));
            } catch (Exception e) {
                return 0;
            }
        });

        llHistoryList.removeAllViews();
        if (historyList.isEmpty()) {
            tvHistoryEmpty.setVisibility(View.VISIBLE);
            llHistoryList.setVisibility(View.GONE);
        } else {
            tvHistoryEmpty.setVisibility(View.GONE);
            llHistoryList.setVisibility(View.VISIBLE);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            for (Object record : historyList) {
                View itemView = LayoutInflater.from(mContext).inflate(R.layout.item_stockin_history, llHistoryList, false);
                TextView tvName = itemView.findViewById(R.id.tvSiItemName);
                TextView tvEpc = itemView.findViewById(R.id.tvSiItemEpc);
                TextView tvType = itemView.findViewById(R.id.tvSiItemType);
                TextView tvTime = itemView.findViewById(R.id.tvSiItemTime);

                if (record instanceof StockInInfo) {
                    StockInInfo si = (StockInInfo) record;
                    tvName.setText(si.toString());
                    tvEpc.setText("TID: " + si.epc);
                    String typeLabel;
                    switch (si.type) {
                        case "BOX": typeLabel = "入库(箱子)"; break;
                        case "CONTENT": typeLabel = "入库(内容物)"; break;
                        default: typeLabel = "入库(独立)"; break;
                    }
                    tvType.setText(typeLabel);
                    tvType.setTextColor(0xFF4CAF50);
                    try { tvTime.setText(sdf.format(new java.util.Date(Long.parseLong(si.timestamp)))); }
                    catch (Exception e) { tvTime.setText(si.timestamp); }
                } else if (record instanceof CheckoutLogInfo) {
                    CheckoutLogInfo cl = (CheckoutLogInfo) record;
                    String action = "BORROW".equals(cl.status) ? "借出" : "归还";
                    tvName.setText(cl.studentId + " - " + action);
                    tvEpc.setText(cl.boxShortId != null ? cl.boxShortId : "");
                    tvType.setText(action);
                    tvType.setTextColor("BORROW".equals(cl.status) ? 0xFFFF5722 : 0xFF4CAF50);
                    try { tvTime.setText(sdf.format(new java.util.Date(Long.parseLong(cl.timestamp)))); }
                    catch (Exception e) { tvTime.setText(cl.timestamp); }
                }
                llHistoryList.addView(itemView);
            }
        }
    }

    // ==================== Edit Mode ====================

    private void toggleEditMode() {
        if (currentStockInInfo == null) return;
        if (isEditMode) {
            exitEditMode();
        } else {
            enterEditMode();
        }
    }

    private void enterEditMode() {
        if (currentStockInInfo == null) return;
        isEditMode = true;

        etDetailShortId.setText(currentStockInInfo.shortId != null ? currentStockInInfo.shortId : "");
        etDetailDesc.setText(currentStockInInfo.description != null ? currentStockInInfo.description : "");
        etDetailCategory.setText(currentStockInInfo.category != null ? currentStockInInfo.category : "");
        etDetailItemNumber.setText(currentStockInInfo.itemNumber != null ? currentStockInInfo.itemNumber : "");
        etDetailShelf.setText(currentStockInInfo.shelf != null ? currentStockInInfo.shelf : "");
        etDetailRoom.setText(currentStockInInfo.room != null ? currentStockInInfo.room : "");

        llEditFields.setVisibility(View.VISIBLE);
        btnEdit.setText(R.string.stockin_cancel_edit);
    }

    private void exitEditMode() {
        isEditMode = false;
        llEditFields.setVisibility(View.GONE);
        btnEdit.setText(R.string.stockin_edit);
    }

    private void saveChanges() {
        if (currentStockInInfo == null) return;

        String shortId = etDetailShortId.getText().toString().trim();
        String desc = etDetailDesc.getText().toString().trim();
        String category = etDetailCategory.getText().toString().trim();
        String itemNumber = etDetailItemNumber.getText().toString().trim();
        String shelf = etDetailShelf.getText().toString().trim();
        String room = etDetailRoom.getText().toString().trim();

        int result = dbHelper.updateStockIn(currentStockInInfo.id,
                shortId, desc, category, itemNumber, shelf, room, null);

        if (result > 0) {
            mContext.showToast(R.string.stockin_saved);
            exitEditMode();
            loadItemDetail();
        } else {
            mContext.showToast(R.string.stockin_save_fail);
        }
    }

    // ==================== Radar Location ====================

    @SuppressLint("LongLogTag")
    private void startLocated() {
        if (inventoryFlag) return;
        if (itemEpc.isEmpty()) {
            Toast.makeText(mContext, "TID 为空，无法定位", Toast.LENGTH_SHORT).show();
            return;
        }

        radarView.clearPanel();

        boolean result = mContext.mReader.startRadarLocation(mContext, itemEpc, IUHF.Bank_TID, 0, new IUHFRadarLocationCallback() {
            @Override
            public void getLocationValue(final List<RadarLocationEntity> list) {
                radarView.bindingData(list, itemEpc);
                if (!TextUtils.isEmpty(itemEpc)) {
                    for (int k = 0; k < list.size(); k++) {
                        if (list.get(k).getTag().equals(itemEpc)) {
                            mContext.playSoundDelayed(list.get(k).getValue());
                        }
                    }
                } else {
                    mContext.playSound(1);
                }
            }

            @Override
            public void getAngleValue(int angle) {
                radarView.setRotation(-angle);
            }
        });

        if (!result) {
            Toast.makeText(mContext, "启动雷达定位失败", Toast.LENGTH_SHORT).show();
            return;
        }

        seekBarPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress2, boolean fromUser) {
                progress = progress2;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int p = 35 - progress;
                mContext.mReader.setDynamicDistance(p);
            }
        });

        seekBarPower.setEnabled(true);
        inventoryFlag = true;
        btnRadarStart.setEnabled(false);
        radarView.startRadar();
        Log.i(TAG, "startLocated success");
    }

    @SuppressLint("LongLogTag")
    private void stopLocated() {
        if (!inventoryFlag) return;
        radarView.stopRadar();

        boolean result = mContext.mReader.stopRadarLocation();
        if (!result) {
            Log.e(TAG, "stopLocated failure");
            Toast.makeText(mContext, R.string.uhf_msg_inventory_stop_fail, Toast.LENGTH_SHORT).show();
        } else {
            Log.i(TAG, "stopLocated success");
            inventoryFlag = false;
            btnRadarStart.setEnabled(true);
        }
        seekBarPower.setOnSeekBarChangeListener(null);
        seekBarPower.setProgress(5);
        seekBarPower.setEnabled(false);
    }

    // ==================== History Collapse/Expand ====================

    private void toggleHistoryVisibility() {
        if (isHistoryExpanded) {
            llHistoryList.setVisibility(View.GONE);
            tvHistoryEmpty.setVisibility(View.GONE);
            RotateAnimation rotate = new RotateAnimation(180f, 0f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotate.setDuration(250);
            rotate.setFillAfter(true);
            ivHistoryArrow.startAnimation(rotate);
            isHistoryExpanded = false;
        } else {
            if (historyList.isEmpty()) {
                tvHistoryEmpty.setVisibility(View.VISIBLE);
                llHistoryList.setVisibility(View.GONE);
            } else {
                tvHistoryEmpty.setVisibility(View.GONE);
                llHistoryList.setVisibility(View.VISIBLE);
            }
            RotateAnimation rotate = new RotateAnimation(0f, 180f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            rotate.setDuration(250);
            rotate.setFillAfter(true);
            ivHistoryArrow.startAnimation(rotate);
            isHistoryExpanded = true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (inventoryFlag) {
            stopLocated();
        }
    }

    @Override
    public void myOnKeyDwon() {
        if (inventoryFlag) {
            stopLocated();
        } else {
            goBack();
        }
    }
}
