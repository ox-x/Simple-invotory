package com.example.uhf.fragment;

import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
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
            if (item.fragmentClass == null && getString(R.string.advanced_clear_database).equals(item.label)) {
                // Clear Database entry - show confirmation dialog
                showClearDatabaseDialog();
            } else if (item.fragmentClass == null && getString(R.string.advanced_change_password).equals(item.label)) {
                // Change Password entry - show change password dialog
                showChangePasswordDialog();
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
        menuItems.add(new DashboardFragment.MenuItem("📋", getString(R.string.advanced_scan), 0xFF2196F3,
                com.example.uhf.fragment.UHFReadTagFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("📡", getString(R.string.advanced_radar), 0xFF4CAF50,
                com.example.uhf.fragment.UHFRadarLocationFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("📍", getString(R.string.advanced_location), 0xFFFF9800,
                com.example.uhf.fragment.UHFLocationFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("⚙️", getString(R.string.advanced_settings), 0xFF607D8B,
                com.example.uhf.fragment.UHFSetFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("✏️", getString(R.string.advanced_read_write), 0xFF9C27B0,
                com.example.uhf.fragment.UHFReadWriteFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("💡", getString(R.string.advanced_light), 0xFFFFEB3B,
                com.example.uhf.fragment.UHFLightFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("🔒", getString(R.string.advanced_lock), 0xFFF44336,
                com.example.uhf.fragment.UHFLockFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("🗑️", getString(R.string.advanced_kill), 0xFF795548,
                com.example.uhf.fragment.UHFKillFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("📝", getString(R.string.advanced_block_write), 0xFF3F51B5,
                com.example.uhf.fragment.BlockWriteFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("🔐", getString(R.string.advanced_permalock), 0xFF009688,
                com.example.uhf.fragment.BlockPermalockFragment.class));
        menuItems.add(new DashboardFragment.MenuItem("🔄", getString(R.string.advanced_upgrade), 0xFF00BCD4,
                com.example.uhf.fragment.UHFUpgradeFragment.class));
        // Clear database (special handling)
        menuItems.add(new DashboardFragment.MenuItem("🗄️", getString(R.string.advanced_clear_database), 0xFFD32F2F, null));
        // Change password (special handling)
        menuItems.add(new DashboardFragment.MenuItem("🔑", getString(R.string.advanced_change_password), 0xFF607D8B, null));
    }

    @Override
    public void myOnKeyDwon() {
        goBack();
    }

    // ==================== Clear Database Dialog ====================

    private void showClearDatabaseDialog() {
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.advanced_clear_db_title)
                .setMessage(R.string.advanced_clear_db_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    DatabaseHelper.getInstance(mContext).clearAllData();
                    Toast.makeText(mContext, R.string.advanced_db_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    // ==================== Change Password Dialog ====================

    private void showChangePasswordDialog() {
        LinearLayout wrapper = new LinearLayout(mContext);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(48, 16, 48, 0);

        final EditText etOldPassword = new EditText(mContext);
        etOldPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etOldPassword.setHint(R.string.advanced_old_pwd_hint);
        etOldPassword.setSingleLine(true);
        wrapper.addView(etOldPassword);

        final EditText etNewPassword = new EditText(mContext);
        etNewPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etNewPassword.setHint(R.string.advanced_new_pwd_hint);
        etNewPassword.setSingleLine(true);
        LinearLayout.LayoutParams mtParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mtParams.topMargin = 16;
        wrapper.addView(etNewPassword, mtParams);

        final EditText etConfirmPassword = new EditText(mContext);
        etConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etConfirmPassword.setHint(R.string.advanced_confirm_pwd_hint);
        etConfirmPassword.setSingleLine(true);
        LinearLayout.LayoutParams mtParams2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mtParams2.topMargin = 16;
        wrapper.addView(etConfirmPassword, mtParams2);

        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.advanced_change_pwd_title)
                .setView(wrapper)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.button_cancel, (d, w) -> d.dismiss())
                .create();
        dialog.setCanceledOnTouchOutside(false);

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String oldPwd = etOldPassword.getText().toString().trim();
                String newPwd = etNewPassword.getText().toString().trim();
                String confirmPwd = etConfirmPassword.getText().toString().trim();

                String currentPwd = DashboardFragment.getAdminPassword(mContext);
                if (!currentPwd.equals(oldPwd)) {
                    Toast.makeText(mContext, R.string.advanced_pwd_old_wrong, Toast.LENGTH_SHORT).show();
                    etOldPassword.setText("");
                    return;
                }
                if (newPwd.isEmpty()) {
                    Toast.makeText(mContext, R.string.advanced_pwd_empty, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPwd.equals(confirmPwd)) {
                    Toast.makeText(mContext, R.string.advanced_pwd_mismatch, Toast.LENGTH_SHORT).show();
                    etConfirmPassword.setText("");
                    return;
                }

                DashboardFragment.setAdminPassword(mContext, newPwd);
                Toast.makeText(mContext, R.string.advanced_pwd_changed, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
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
