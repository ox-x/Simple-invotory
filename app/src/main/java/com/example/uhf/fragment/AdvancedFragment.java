package com.example.uhf.fragment;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.db.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Options Fragment (高级选项页面).
 * Shows all UHF tool features that are not part of the core business functions.
 */
public class AdvancedFragment extends KeyDwonFragment {

    private UHFMainActivity mContext;
    private GridView gvAdvanced;
    private Button btnBack;
    private AdvancedAdapter adapter;
    private List<DashboardFragment.MenuItem> menuItems = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_advanced, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        buildMenuItems();
        gvAdvanced = getView().findViewById(R.id.gvAdvanced);
        btnBack = getView().findViewById(R.id.btnAdvancedBack);
        adapter = new AdvancedAdapter();
        gvAdvanced.setAdapter(adapter);
        gvAdvanced.setOnItemClickListener((parent, view, position, id) -> {
            DashboardFragment.MenuItem item = menuItems.get(position);
            if (item.fragmentClass == null) {
                // "清除数据库" entry - show confirmation dialog
                showClearDatabaseDialog();
            } else {
                mContext.openFeature(item.fragmentClass, item.label);
            }
        });
        btnBack.setOnClickListener(v -> goBack());
    }

    private void goBack() {
        if (getFragmentManager() != null) {
            getFragmentManager().popBackStack();
        }
    }

    private void buildMenuItems() {
        menuItems.clear();
        menuItems.add(new DashboardFragment.MenuItem("📋", "扫描", 0xFF2196F3,
                com.example.uhf.fragment.UHFReadTagFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("📡", "雷达", 0xFF4CAF50,
                com.example.uhf.fragment.UHFRadarLocationFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("📍", "定位", 0xFFFF9800,
                com.example.uhf.fragment.UHFLocationFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("⚙️", "设置", 0xFF607D8B,
                com.example.uhf.fragment.UHFSetFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("✏️", "读写", 0xFF9C27B0,
                com.example.uhf.fragment.UHFReadWriteFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("💡", "点亮", 0xFFFFEB3B,
                com.example.uhf.fragment.UHFLightFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("🔒", "锁定", 0xFFF44336,
                com.example.uhf.fragment.UHFLockFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("🗑️", "销毁", 0xFF795548,
                com.example.uhf.fragment.UHFKillFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("📝", "块写", 0xFF3F51B5,
                com.example.uhf.fragment.BlockWriteFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("🔐", "永久锁", 0xFF009688,
                com.example.uhf.fragment.BlockPermalockFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("🔄", "升级", 0xFF00BCD4,
                com.example.uhf.fragment.UHFUpgradeFragment.class));
        // Clear database (special handling)
        menuItems.add(new DashboardFragment.MenuItem("🗄️", "清除数据库", 0xFFD32F2F, null));
    }

    @Override
    public void myOnKeyDwon() {
        goBack();
    }

    // ==================== Clear Database Dialog ====================

    private void showClearDatabaseDialog() {
        new AlertDialog.Builder(mContext)
                .setTitle("清除数据库")
                .setMessage("确定要清除所有数据库数据吗？此操作不可恢复。")
                .setPositiveButton("确定", (dialog, which) -> {
                    DatabaseHelper.getInstance(mContext).clearAllData();
                    Toast.makeText(mContext, "数据库已清除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== Adapter ====================

    private class AdvancedAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return menuItems.size();
        }

        @Override
        public Object getItem(int position) {
            return menuItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.item_dashboard, parent, false);
                holder = new ViewHolder();
                holder.tvIcon = convertView.findViewById(R.id.tvDashIcon);
                holder.tvLabel = convertView.findViewById(R.id.tvDashLabel);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            DashboardFragment.MenuItem item = menuItems.get(position);
            holder.tvIcon.setText(item.icon);
            holder.tvLabel.setText(item.label);

            // Set dynamic color for the icon circle
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(item.color);
            holder.tvIcon.setBackground(bg);

            return convertView;
        }

        class ViewHolder {
            TextView tvIcon;
            TextView tvLabel;
        }
    }
}
