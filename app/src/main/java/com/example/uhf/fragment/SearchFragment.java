package com.example.uhf.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.db.BoxInfo;
import com.example.uhf.db.CheckoutLogInfo;
import com.example.uhf.db.ContentInfo;
import com.example.uhf.db.DatabaseHelper;
import com.example.uhf.db.SearchResult;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Smart Search Fragment.
 * Provides fuzzy/partial search across all assets by Short ID, EPC, or Description.
 * Clicking a BOX result shows its contents with checkout status.
 */
public class SearchFragment extends KeyDwonFragment {

    private static final String TAG = "SearchFragment";

    private UHFMainActivity mContext;
    private DatabaseHelper dbHelper;

    // Search bar
    private EditText etSearchQuery;
    private Button btnSearch;
    private TextView tvSearchResultCount;
    private ListView lvSearchResults;

    // Box detail panel
    private LinearLayout llSearchBoxDetail;
    private Button btnSearchBackToList;
    private TextView tvSearchBoxTitle, tvSearchBoxEpc, tvSearchBoxStatus, tvSearchBoxContentsTitle;
    private ListView lvSearchBoxContents;

    private SearchResultsAdapter adapter;
    private List<SearchResult> searchResults = new ArrayList<>();

    // Box detail data
    private BoxContentAdapter boxContentAdapter;
    private List<ContentInfo> boxContents = new ArrayList<>();
    private String currentBoxEpc = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        dbHelper = DatabaseHelper.getInstance(mContext);

        initViews();
    }

    private void initViews() {
        View v = getView();

        // Search bar
        etSearchQuery = v.findViewById(R.id.etSearchQuery);
        btnSearch = v.findViewById(R.id.btnSearch);
        tvSearchResultCount = v.findViewById(R.id.tvSearchResultCount);
        lvSearchResults = v.findViewById(R.id.lvSearchResults);

        // Box detail panel
        llSearchBoxDetail = v.findViewById(R.id.llSearchBoxDetail);
        btnSearchBackToList = v.findViewById(R.id.btnSearchBackToList);
        tvSearchBoxTitle = v.findViewById(R.id.tvSearchBoxTitle);
        tvSearchBoxEpc = v.findViewById(R.id.tvSearchBoxEpc);
        tvSearchBoxStatus = v.findViewById(R.id.tvSearchBoxStatus);
        tvSearchBoxContentsTitle = v.findViewById(R.id.tvSearchBoxContentsTitle);
        lvSearchBoxContents = v.findViewById(R.id.lvSearchBoxContents);

        adapter = new SearchResultsAdapter();
        lvSearchResults.setAdapter(adapter);

        boxContentAdapter = new BoxContentAdapter();
        lvSearchBoxContents.setAdapter(boxContentAdapter);

        // Search button click
        btnSearch.setOnClickListener(view -> performSearch());

        // Real-time fuzzy search as user types
        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= 2) {
                    performSearch();
                } else if (s.length() == 0) {
                    searchResults.clear();
                    adapter.notifyDataSetChanged();
                    tvSearchResultCount.setText("");
                }
            }
        });

        // Click on result
        lvSearchResults.setOnItemClickListener((parent, view, position, id) -> {
            SearchResult result = searchResults.get(position);
            if ("BOX".equals(result.type)) {
                // Show box contents with status
                showBoxDetail(result);
            } else if ("CONTENT".equals(result.type) && result.boxEpc != null && !result.boxEpc.isEmpty()) {
                // Show parent box detail
                BoxInfo box = dbHelper.getBoxByEpc(result.boxEpc);
                if (box != null) {
                    SearchResult boxResult = new SearchResult();
                    boxResult.type = "BOX";
                    boxResult.epc = box.epc;
                    boxResult.shortId = box.shortId;
                    boxResult.description = box.description;
                    boxResult.photoPath = box.photoPath;
                    showBoxDetail(boxResult);
                }
            } else {
                // Copy TID to clipboard
                if (result.epc != null) {
                    ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("RFID TID", result.epc);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(mContext, "TID copied: " + result.epc, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Back button from box detail
        btnSearchBackToList.setOnClickListener(view -> hideBoxDetail());
    }

    private void performSearch() {
        String query = etSearchQuery.getText().toString().trim();
        if (query.isEmpty()) return;

        // Hide box detail if showing
        hideBoxDetail();

        searchResults.clear();
        searchResults.addAll(dbHelper.fuzzySearch(query));
        adapter.notifyDataSetChanged();

        if (searchResults.isEmpty()) {
            tvSearchResultCount.setText(R.string.search_no_results);
        } else {
            tvSearchResultCount.setText(searchResults.size() + " results found");
        }
    }

    private void showBoxDetail(SearchResult boxResult) {
        currentBoxEpc = boxResult.epc;

        // Set title
        String title = boxResult.shortId != null && !boxResult.shortId.isEmpty()
                ? boxResult.shortId : boxResult.epc;
        tvSearchBoxTitle.setText(title);
        tvSearchBoxEpc.setText("TID: " + boxResult.epc);

        // Show checkout status
        CheckoutLogInfo latestLog = dbHelper.getLatestCheckoutLog(boxResult.epc);
        if (latestLog != null) {
            String statusText;
            if ("COMPLETE".equals(latestLog.status)) {
                statusText = getString(R.string.status_checked_out) + " (" + latestLog.studentName + ")";
                tvSearchBoxStatus.setTextColor(0xFFFF5722);
            } else {
                statusText = getString(R.string.status_checked_out) + " - " + getString(R.string.status_missing);
                tvSearchBoxStatus.setTextColor(0xFFF44336);
            }
            try {
                long ts = Long.parseLong(latestLog.timestamp);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                statusText += "\n" + getString(R.string.warehouse_last_checkout, sdf.format(new Date(ts)));
            } catch (Exception e) {
                // ignore
            }
            tvSearchBoxStatus.setText(statusText);
        } else {
            tvSearchBoxStatus.setText(getString(R.string.warehouse_box_status, getString(R.string.status_in_stock)));
            tvSearchBoxStatus.setTextColor(0xFF4CAF50);
        }

        // Load contents
        boxContents.clear();
        boxContents.addAll(dbHelper.getContentsByBoxEpc(boxResult.epc));
        boxContentAdapter.notifyDataSetChanged();

        tvSearchBoxContentsTitle.setText(getString(R.string.search_box_contents, boxContents.size()));

        // Show detail panel, hide search results
        lvSearchResults.setVisibility(View.GONE);
        llSearchBoxDetail.setVisibility(View.VISIBLE);
    }

    private void hideBoxDetail() {
        lvSearchResults.setVisibility(View.VISIBLE);
        llSearchBoxDetail.setVisibility(View.GONE);
    }

    @Override
    public void myOnKeyDwon() {
        // Hardware scan button: trigger search
        performSearch();
    }

    // ==================== Search Results Adapter ====================

    private class SearchResultsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return searchResults.size();
        }

        @Override
        public Object getItem(int position) {
            return searchResults.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.item_kitting_content, parent, false);
                holder = new ViewHolder();
                holder.ivPhoto = convertView.findViewById(R.id.ivItemPhoto);
                holder.tvName = convertView.findViewById(R.id.tvItemName);
                holder.tvEpc = convertView.findViewById(R.id.tvItemEpc);
                holder.tvDesc = convertView.findViewById(R.id.tvItemDescription);
                holder.btnDelete = convertView.findViewById(R.id.btnDeleteItem);
                holder.btnDelete.setVisibility(View.GONE);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            SearchResult item = searchResults.get(position);

            // Build display name
            String displayName = "[" + item.type + "] ";
            if (item.shortId != null && !item.shortId.isEmpty()) {
                displayName += item.shortId;
            } else {
                displayName += item.epc;
            }
            holder.tvName.setText(displayName);

            // EPC
            holder.tvEpc.setText("TID: " + (item.epc != null ? item.epc : ""));

            // Description
            String desc = "";
            if (item.description != null && !item.description.isEmpty()) {
                desc = item.description;
            }
            if (item.boxEpc != null && !item.boxEpc.isEmpty()) {
                if (!desc.isEmpty()) desc += " | ";
                desc += "Box: " + item.boxEpc;
            }
            holder.tvDesc.setText(desc);

            // Photo
            if (item.photoPath != null && !item.photoPath.isEmpty()) {
                File f = new File(item.photoPath);
                if (f.exists()) {
                    holder.ivPhoto.setImageBitmap(BitmapFactory.decodeFile(item.photoPath));
                } else {
                    holder.ivPhoto.setImageDrawable(null);
                    holder.ivPhoto.setBackgroundColor(0xFFEEEEEE);
                }
            } else {
                holder.ivPhoto.setImageDrawable(null);
                holder.ivPhoto.setBackgroundColor(0xFFEEEEEE);
            }

            // Click name to open item detail
            final String epc = item.epc;
            holder.tvName.setOnClickListener(v -> {
                if (epc != null) mContext.openItemDetail(epc);
            });

            return convertView;
        }

        class ViewHolder {
            ImageView ivPhoto;
            TextView tvName, tvEpc, tvDesc;
            Button btnDelete;
        }
    }

    // ==================== Box Content Adapter (with status) ====================

    private class BoxContentAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return boxContents.size();
        }

        @Override
        public Object getItem(int position) {
            return boxContents.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.item_checkout_content, parent, false);
                holder = new ViewHolder();
                holder.ivPhoto = convertView.findViewById(R.id.ivCheckoutPhoto);
                holder.tvName = convertView.findViewById(R.id.tvCheckoutItemName);
                holder.tvEpc = convertView.findViewById(R.id.tvCheckoutItemEpc);
                holder.tvStatus = convertView.findViewById(R.id.tvCheckoutStatus);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            ContentInfo item = boxContents.get(position);
            holder.tvName.setText(item.toString());
            holder.tvEpc.setText("TID: " + item.epc);

            // Show real status
            String status = dbHelper.getContentStatus(currentBoxEpc, item.epc, item.shortId, item.description);
            switch (status) {
                case "CHECKED_OUT":
                    holder.tvStatus.setText("\u2717"); // ✗
                    holder.tvStatus.setTextColor(0xFFFF5722);
                    convertView.setBackgroundColor(0x22FF5722);
                    break;
                case "MISSING":
                    holder.tvStatus.setText("!");
                    holder.tvStatus.setTextColor(0xFFF44336);
                    convertView.setBackgroundColor(0x22F44336);
                    break;
                default: // IN_STOCK
                    holder.tvStatus.setText("\u2713"); // ✓
                    holder.tvStatus.setTextColor(0xFF4CAF50);
                    convertView.setBackgroundColor(0x00000000);
                    break;
            }

            // Photo
            if (item.photoPath != null && !item.photoPath.isEmpty()) {
                File f = new File(item.photoPath);
                if (f.exists()) {
                    try {
                        holder.ivPhoto.setImageBitmap(BitmapFactory.decodeFile(item.photoPath));
                    } catch (Exception e) {
                        holder.ivPhoto.setImageDrawable(null);
                    }
                } else {
                    holder.ivPhoto.setImageDrawable(null);
                    holder.ivPhoto.setBackgroundColor(0xFFEEEEEE);
                }
            } else {
                holder.ivPhoto.setImageDrawable(null);
                holder.ivPhoto.setBackgroundColor(0xFFEEEEEE);
            }

            // Click name to open item detail
            final String epc = item.epc;
            holder.tvName.setOnClickListener(v -> {
                if (epc != null) mContext.openItemDetail(epc);
            });

            // Click row to copy TID
            convertView.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("RFID TID", item.epc);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(mContext, "TID copied: " + item.epc, Toast.LENGTH_SHORT).show();
            });

            return convertView;
        }

        class ViewHolder {
            ImageView ivPhoto;
            TextView tvName, tvEpc, tvStatus;
        }
    }
}
