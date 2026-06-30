package com.example.uhf.fragment;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.uhf.R;
import com.example.uhf.activity.UHFMainActivity;
import com.example.uhf.tools.EmulatorDetector;
import com.example.uhf.tools.OperationLogManager;
import com.example.uhf.tools.WirelessLogServer;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.List;
import java.util.Locale;

/**
 * 操作日志查看 Fragment
 * 支持应用内实时查看、清空、分享日志文件，以及无线Web查看
 */
public class LogViewerFragment extends KeyDwonFragment {

    private UHFMainActivity mContext;
    private RecyclerView recyclerView;
    private TextView tvHint, tvWirelessStatus;
    private LogAdapter adapter;
    private OperationLogManager logManager;
    private static WirelessLogServer wirelessServer;
    private Button btnWireless;
    private LinearLayout llQrCode;
    private ImageView ivQrCode;

    // 自动刷新
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshTask = new Runnable() {
        @Override
        public void run() {
            refreshLogs();
            autoRefreshHandler.postDelayed(this, 2000);
        }
    };
    private boolean autoRefreshEnabled = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log_viewer, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = (UHFMainActivity) getActivity();
        mContext.currentFragment = this;
        logManager = OperationLogManager.getInstance();

        View v = getView();

        recyclerView = v.findViewById(R.id.rvLogs);
        recyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        tvHint = v.findViewById(R.id.tvLogHint);
        tvWirelessStatus = v.findViewById(R.id.tvWirelessStatus);
        llQrCode = v.findViewById(R.id.llQrCode);
        ivQrCode = v.findViewById(R.id.ivQrCode);

        v.findViewById(R.id.btnRefresh).setOnClickListener(v2 -> {
            refreshLogs();
            Toast.makeText(mContext, getString(R.string.oplog_refreshed), Toast.LENGTH_SHORT).show();
        });

        btnWireless = v.findViewById(R.id.btnWireless);
        btnWireless.setOnClickListener(v2 -> toggleWirelessServer());

        // 检测用户是否手动滚离底部
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView rv, int newState) {
                super.onScrollStateChanged(rv, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    autoRefreshEnabled = false;
                }
            }
        });

        // 检查无线服务器是否已在运行（跨 Fragment 重建保持）
        if (wirelessServer != null && wirelessServer.isRunning()) {
            showServerRunning();
        }

        refreshLogs();
        // 启动自动刷新（每2秒刷新一次，确保新日志实时显示）
        autoRefreshHandler.post(autoRefreshTask);
    }

    private void refreshLogs() {
        List<OperationLogManager.LogEntry> logs = logManager.getLogs();
        if (adapter == null) {
            adapter = new LogAdapter(logs);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(logs);
        }

        // 无日志时显示提示
        tvHint.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(logs.isEmpty() ? View.GONE : View.VISIBLE);

        // 滚动到最新（仅在用户未手动滚动时）
        if (!logs.isEmpty() && autoRefreshEnabled) {
            recyclerView.smoothScrollToPosition(logs.size() - 1);
        }
    }

    /** \u5207\u6362\u65E0\u7EBF\u670D\u52A1\u5668\u5F00\u542F/\u5173\u95ED */
    private void toggleWirelessServer() {
        // 先检查是否已有服务器在运行（当前实例或之前的实例）
        if (wirelessServer != null && wirelessServer.isRunning()) {
            showServerRunning();
            return;
        }

        // 开启
        wirelessServer = new WirelessLogServer(mContext, () -> {
            List<OperationLogManager.LogEntry> logs = logManager.getLogs();
            return WirelessLogServer.buildLogRows(logs);
        });
        wirelessServer.start();

        // 延迟200ms验证服务器是否真正启动成功
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (wirelessServer != null && wirelessServer.isRunning()) {
                showServerRunning();

                // 自动打开本机浏览器
                openBrowser("http://127.0.0.1:8080");

                Toast.makeText(mContext,
                        getString(R.string.oplog_started_toast),
                        Toast.LENGTH_SHORT).show();
            } else {
                // 服务器启动失败
                llQrCode.setVisibility(View.GONE);
                btnWireless.setText(R.string.oplog_start_failed);
                btnWireless.setBackgroundResource(0);
                btnWireless.setBackgroundColor(0xFFE53935);
                tvWirelessStatus.setVisibility(View.VISIBLE);
                tvWirelessStatus.setText(R.string.oplog_start_failed_msg);
                tvWirelessStatus.setOnClickListener(null);
                Toast.makeText(mContext, getString(R.string.oplog_start_failed_msg), Toast.LENGTH_SHORT).show();
            }
        }, 200);
    }

    /** 显示服务器运行中状态 */
    private void showServerRunning() {
        if (wirelessServer == null || !wirelessServer.isRunning()) {
            // 隐藏二维码
            llQrCode.setVisibility(View.GONE);
            // 尝试清理旧的服务器实例
            if (wirelessServer != null) {
                wirelessServer.stop();
                wirelessServer = null;
            }
            return;
        }
        String url = wirelessServer.getAccessUrl();
        btnWireless.setText(R.string.oplog_running);
        btnWireless.setBackgroundResource(0);
        btnWireless.setBackgroundColor(0xFF43A047);
        tvWirelessStatus.setVisibility(View.VISIBLE);

        boolean isEmulator = EmulatorDetector.isEmulator();
        if (isEmulator) {
            tvWirelessStatus.setText(R.string.oplog_emulator_instruction);
        } else {
            tvWirelessStatus.setText(String.format(getString(R.string.oplog_network_instruction), url));
        }

        // 点击状态栏打开浏览器
        tvWirelessStatus.setOnClickListener(v -> openBrowser("http://127.0.0.1:8080"));

        // 生成并显示二维码
        Bitmap qrBitmap = generateQrCode(url, 400);
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap);
            llQrCode.setVisibility(View.VISIBLE);
        }

        Toast.makeText(mContext, String.format(getString(R.string.oplog_running_msg), url), Toast.LENGTH_SHORT).show();
    }

    /**
     * 使用 ZXing 生成二维码 Bitmap
     * @param text 二维码内容（URL）
     * @param size 图片尺寸（像素）
     * @return 二维码 Bitmap，失败返回 null
     */
    private Bitmap generateQrCode(String text, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException e) {
            Log.e("LogViewer", "生成二维码失败", e);
            return null;
        }
    }

    /** 用设备浏览器打开指定网址 */
    private void openBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("LogViewer", "打开浏览器失败", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        autoRefreshHandler.removeCallbacks(autoRefreshTask);
        // 不停止无线服务器，让其在后台继续运行
        // 服务器将在应用完全退出时随进程终止
    }

    @Override
    public void myOnKeyDwon() {
        // 扫描键触发刷新
        refreshLogs();
    }

    // ==================== RecyclerView Adapter ====================

    static class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {
        private List<OperationLogManager.LogEntry> logs;

        LogAdapter(List<OperationLogManager.LogEntry> logs) {
            this.logs = logs;
        }

        void updateData(List<OperationLogManager.LogEntry> logs) {
            this.logs = logs;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_log_entry, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int pos) {
            OperationLogManager.LogEntry entry = logs.get(pos);
            h.tvTime.setText(entry.timestamp);

            // 判断系统语言，非中文时翻译type和message
            boolean isChinese = Locale.getDefault().getLanguage().equals("zh");
            String typeText = isChinese ? entry.type : WirelessLogServer.translateType(entry.type);
            String msgText = isChinese ? entry.message : WirelessLogServer.translateMessage(entry.message);
            h.tvType.setText(typeText);
            h.tvMessage.setText(msgText);

            // 根据类型设置标签颜色
            int typeColor = getTypeColor(entry.type);
            h.tvType.setBackgroundColor(typeColor);
        }

        @Override
        public int getItemCount() {
            return logs.size();
        }

        private int getTypeColor(String type) {
            if (type == null) return 0xFF757575;
            if (type.contains("借出") || type.contains("Borrow")) return 0xFFFB8C00;
            if (type.contains("归还") || type.contains("Return")) return 0xFF43A047;
            if (type.contains("入库") || type.contains("StockIn")) return 0xFF1E88E5;
            if (type.contains("Kitting")) return 0xFF8E24AA;
            if (type.contains("查询") || type.contains("Search")) return 0xFF546E7A;
            if (type.contains("导出文件")) return 0xFF00ACC1;
            if (type.contains("RFID标签")) return 0xFF78909C;
            return 0xFF757575;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTime, tvType, tvMessage;

            ViewHolder(View v) {
                super(v);
                tvTime = v.findViewById(R.id.tvLogTime);
                tvType = v.findViewById(R.id.tvLogType);
                tvMessage = v.findViewById(R.id.tvLogMessage);
            }
        }
    }
}
