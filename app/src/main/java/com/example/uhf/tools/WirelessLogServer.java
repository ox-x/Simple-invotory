package com.example.uhf.tools;

import android.content.Context;
import android.util.Log;

import com.example.uhf.db.BoxInfo;
import com.example.uhf.db.ContentInfo;
import com.example.uhf.db.DatabaseHelper;
import com.example.uhf.db.CheckoutLogInfo;
import com.example.uhf.db.DisplayItem;
import com.example.uhf.db.StockInInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 无线仓库管理 & 日志查看服务器
 * 在同一 WiFi 局域网内通过浏览器访问 http://设备IP:8080 查看仓库和操作日志
 * 零外部依赖，仅使用 Java 标准库 + Android API
 */
public class WirelessLogServer {

    private static final String TAG = "WirelessLog";
    private static final int PORT = 8080;

    private ServerSocket serverSocket;
    private boolean running = false;
    private Thread serverThread;
    private String deviceIp = "";

    // 日志提供者（可选）
    public interface LogProvider {
        String getLogHtmlContent();
    }
    private LogProvider logProvider;
    private static boolean isChinese = false;

    // 数据库访问（可选，提供后开启仓库管理功能）
    private DatabaseHelper dbHelper;

    // ==================== 构造方法 ====================

    /** 仅日志模式（向后兼容） */
    public WirelessLogServer(LogProvider provider) {
        this.logProvider = provider;
        this.deviceIp = getDeviceIpAddress();
    }

    /** 完整模式：仓库管理 + 日志查看 */
    public WirelessLogServer(Context context, LogProvider provider) {
        this.logProvider = provider;
        this.deviceIp = getDeviceIpAddress();
        // 检测设备语言用于网页国际化
        try {
            Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            this.isChinese = locale.getLanguage().equals("zh");
        } catch (Exception ignored) {}
        try {
            this.dbHelper = DatabaseHelper.getInstance(context);
        } catch (Exception e) {
            Log.e(TAG, "初始化数据库失败", e);
        }
    }

    /**
     * 获取设备当前可路由的 IP 地址。
     * 三重回退策略：
     * 1. 优先返回非 10.x.x.x 的真实网络 IP（真机 WiFi）
     * 2. 其次返回 10.x.x.x 但非 10.0.2.x 的 IP
     * 3. 最后返回 10.0.2.x（模拟器环境）
     */
    public static String getDeviceIpAddress() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            if (nis == null) return "127.0.0.1";
            List<NetworkInterface> interfaces = Collections.list(nis);

            // 第一遍：找真实网络 IP（跳过模拟器 NAT 范围 10.x.x.x）
            for (NetworkInterface ni : interfaces) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                String niName = ni.getName() != null ? ni.getName().toLowerCase() : "";
                if (niName.contains("docker") || niName.contains("veth")) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("127.") && !ip.startsWith("10.")) {
                            return ip;
                        }
                    }
                }
            }

            // 第二遍：允许 10.x.x.x 但排除 10.0.2.x（模拟器内部虚拟网段）
            for (NetworkInterface ni : interfaces) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("127.") && !ip.startsWith("10.0.2.")) {
                            return ip;
                        }
                    }
                }
            }

            // 第三遍：最终回退，接受 10.0.2.x（模拟器 NAT IP）
            for (NetworkInterface ni : interfaces) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("127.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取IP地址失败", e);
        }
        return "127.0.0.1";
    }

    public String getAccessUrl() {
        return "http://" + deviceIp + ":" + PORT;
    }

    public boolean isRunning() {
        return running;
    }

    /** 启动HTTP服务器（非阻塞，在后台线程运行） */
    public void start() {
        if (running) return;
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                running = true;
                Log.i(TAG, "日志服务器已启动: " + getAccessUrl());

                while (running) {
                    try {
                        Socket client = serverSocket.accept();
                        handleClient(client);
                    } catch (Exception e) {
                        if (running) Log.e(TAG, "处理客户端连接异常", e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "启动服务器失败", e);
                running = false;
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    /** 停止服务器 */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        if (serverThread != null) serverThread.interrupt();
    }

    /** 处理单个HTTP客户端请求 */
    private void handleClient(Socket client) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));
            OutputStream out = client.getOutputStream();

            // 读取请求行
            String requestLine = reader.readLine();
            if (requestLine == null) {
                client.close();
                return;
            }
            Log.d(TAG, "请求: " + requestLine);

            // 解析路径和查询参数
            String path = "/";
            String query = "";
            if (requestLine.startsWith("GET ")) {
                String fullPath = requestLine.substring(4, requestLine.lastIndexOf(" HTTP"));
                int qIdx = fullPath.indexOf('?');
                if (qIdx >= 0) {
                    path = fullPath.substring(0, qIdx);
                    query = fullPath.substring(qIdx + 1);
                } else {
                    path = fullPath;
                }
            }

            // 跳过剩余请求头
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty());

            // 路由
            if (path.equals("/logs")) {
                sendHtmlResponse(out, buildLogsPage());
            } else if (path.equals("/detail")) {
                String epc = "";
                if (query != null && !query.isEmpty()) {
                    for (String param : query.split("&")) {
                        String[] kv = param.split("=", 2);
                        if (kv.length == 2 && kv[0].equals("epc")) {
                            epc = urlDecode(kv[1]);
                        }
                    }
                }
                sendHtmlResponse(out, buildDetailPage(epc));
            } else if (path.equals("/api/data")) {
                sendJsonResponse(out, buildDataJson());
            } else {
                sendHtmlResponse(out, buildWarehousePage());
            }

            client.close();
        } catch (Exception e) {
            Log.e(TAG, "处理HTTP请求失败", e);
        }
    }

    private void sendHtmlResponse(OutputStream out, String html) throws Exception {
        byte[] response = ("HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + html.getBytes("UTF-8").length + "\r\n"
                + "Connection: close\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "\r\n"
                + html).getBytes("UTF-8");
        out.write(response);
        out.flush();
    }

    private void sendJsonResponse(OutputStream out, String json) throws Exception {
        byte[] response = ("HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + json.getBytes("UTF-8").length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + json).getBytes("UTF-8");
        out.write(response);
        out.flush();
    }

    // ==================== 页面路由 ====================

    /** 构建导航栏 HTML */
    private String navHtml(String active) {
        String whActive = active.equals("warehouse") ? " class='active'" : "";
        String logActive = active.equals("logs") ? " class='active'" : "";
        return "<div class='nav'>"
                + "<a href='/'" + whActive + ">\uD83D\uDCE6 " + t("Warehouse", "\u4ED3\u5E93\u7BA1\u7406") + "</a>"
                + "<a href='/logs' " + logActive + ">\uD83D\uDCCB " + t("Operation Logs", "操作日志") + "</a>"
                + "</div>";
    }

    // ==================== 仓库管理页面 ====================

    /** 构建仓库管理首页（全部物品渲染，JS前端即时搜索/筛选，每10秒轮询更新数据） */
    private String buildWarehousePage() {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        // 统计数据
        int boxCount = 0, itemCount = 0, inStock = 0, borrowed = 0, standaloneCount = 0;

        // 收集所有显示数据
        List<DisplayItem> allGroups = new ArrayList<>();
        Map<String, List<DisplayItem>> allChildren = new LinkedHashMap<>();
        List<DisplayItem> standaloneItems = new ArrayList<>();

        if (dbHelper != null) {
            try {
                List<BoxInfo> boxes = dbHelper.getAllBoxes();
                for (BoxInfo box : boxes) {
                    DisplayItem group = DisplayItem.fromBox(box);
                    group.borrowStatus = dbHelper.getItemBorrowStatus(box.epc);
                    StockInInfo si = dbHelper.getLatestStockIn(box.epc);
                    if (si != null) {
                        if (si.description != null) group.description = si.description;
                        if (si.category != null) group.category = si.category;
                        if (si.itemNumber != null) group.itemNumber = si.itemNumber;
                        if (si.shelf != null) group.shelf = si.shelf;
                        if (si.room != null) group.room = si.room;
                    }
                    List<ContentInfo> contents = dbHelper.getContentsByBoxEpc(box.epc);
                    List<DisplayItem> children = new ArrayList<>();
                    for (ContentInfo c : contents) {
                        DisplayItem child = DisplayItem.fromContent(c);
                        child.borrowStatus = dbHelper.getItemBorrowStatus(c.epc);
                        child.boxName = group.name;
                        children.add(child);
                    }
                    allGroups.add(group);
                    allChildren.put(box.epc, children);
                }
                List<StockInInfo> standalones = dbHelper.getStandaloneItems();
                for (StockInInfo si : standalones) {
                    DisplayItem item = DisplayItem.fromStandalone(si);
                    item.borrowStatus = dbHelper.getItemBorrowStatus(si.epc);
                    if (si.description != null) item.description = si.description;
                    if (si.category != null) item.category = si.category;
                    if (si.itemNumber != null) item.itemNumber = si.itemNumber;
                    if (si.shelf != null) item.shelf = si.shelf;
                    if (si.room != null) item.room = si.room;
                    standaloneItems.add(item);
                }
            } catch (Exception e) { Log.e(TAG, "读取仓库数据失败", e); }
        }

        // 计算统计
        for (DisplayItem g : allGroups) {
            boxCount++; itemCount++;
            if ("BORROWED".equals(g.borrowStatus)) borrowed++; else inStock++;
            List<DisplayItem> kids = allChildren.getOrDefault(g.epc, new ArrayList<>());
            for (DisplayItem c : kids) { itemCount++; if ("BORROWED".equals(c.borrowStatus)) borrowed++; else inStock++; }
        }
        for (DisplayItem s : standaloneItems) { standaloneCount++; itemCount++; if ("BORROWED".equals(s.borrowStatus)) borrowed++; else inStock++; }

        // 生成HTML（全部渲染，JS前端过滤）
        StringBuilder itemsHtml = new StringBuilder();

        for (DisplayItem group : allGroups) {
            String statusClass = "BORROWED".equals(group.borrowStatus) ? "status-borrowed" : "status-instock";
            String statusText = "BORROWED".equals(group.borrowStatus) ? t("Borrowed", "\u5DF2\u501F\u51FA") : t("In Stock", "\u5728\u5E93");
            List<DisplayItem> kids = allChildren.getOrDefault(group.epc, new ArrayList<>());
            StringBuilder extraInfo = new StringBuilder();
            if (group.description != null && !group.description.isEmpty()) extraInfo.append(" | ").append(escapeHtml(group.description));
            if (group.category != null && !group.category.isEmpty()) extraInfo.append(" | ").append(escapeHtml(group.category));
            if (group.itemNumber != null && !group.itemNumber.isEmpty()) extraInfo.append(" | " + t("Item No:", "\u8D27\u53F7:")).append(escapeHtml(group.itemNumber));
            extraInfo.append(kids.size() > 0 ? " | " + kids.size() + t(" items", "\u5B50\u9879") : "");
            String descAttr = (group.description != null ? group.description : "") + " " + (group.category != null ? group.category : "") + " " + (group.itemNumber != null ? group.itemNumber : "");

            itemsHtml.append("<div class='item-group' data-epc='").append(escapeHtml(group.epc))
                     .append("' data-name='").append(escapeHtml(group.name))
                     .append("' data-status='").append(group.borrowStatus)
                     .append("' data-desc='").append(escapeHtml(descAttr)).append("'>")
                     .append("<div class='item-row' onclick='toggleGroup(\"").append(escapeHtml(group.epc)).append("\")'>")
                     .append("<span class='item-icon'>\uD83D\uDCE6</span>")
                     .append("<span class='item-name'><a href='/detail?epc=").append(escapeHtml(group.epc))
                     .append("' onclick='event.stopPropagation()'>").append(escapeHtml(group.name)).append("</a></span>")
                     .append("<span class='item-epc'>").append(escapeHtml(truncateEpc(group.epc))).append("</span>")
                     .append("<span class='item-info'>").append(extraInfo.toString()).append("</span>")
                     .append("<span class='item-status ").append(statusClass).append("'>").append(statusText).append("</span>")
                     .append("<span class='arrow' id='arrow-").append(escapeHtml(group.epc)).append("'>&#9660;</span>")
                     .append("</div>");

            if (!kids.isEmpty()) {
                itemsHtml.append("<div class='children' id='children-").append(escapeHtml(group.epc)).append("'>");
                for (DisplayItem c : kids) {
                    String cs = "BORROWED".equals(c.borrowStatus) ? t("Borrowed", "\u5DF2\u501F\u51FA") : t("In Stock", "\u5728\u5E93");
                    String cc = "BORROWED".equals(c.borrowStatus) ? "status-borrowed" : "status-instock";
                    String cDesc = c.description != null ? c.description : "";
                    itemsHtml.append("<div class='child-row' data-epc='").append(escapeHtml(c.epc))
                             .append("' data-name='").append(escapeHtml(c.name))
                             .append("' data-status='").append(c.borrowStatus)
                             .append("' data-desc='").append(escapeHtml(cDesc)).append("'>")
                             .append("<span class='child-icon'>\uD83D\uDCC4</span>")
                             .append("<span class='item-name'><a href='/detail?epc=").append(escapeHtml(c.epc)).append("'>")
                             .append(escapeHtml(c.name)).append("</a></span>")
                             .append("<span class='item-epc'>").append(escapeHtml(truncateEpc(c.epc))).append("</span>")
                             .append("<span class='item-info'>").append(cDesc.isEmpty() ? "" : " | " + escapeHtml(cDesc)).append("</span>")
                             .append("<span class='item-status ").append(cc).append("'>").append(cs).append("</span>")
                             .append("</div>");
                }
                itemsHtml.append("</div>");
            } else {
                itemsHtml.append("<div class='children' id='children-").append(escapeHtml(group.epc)).append("' style='display:none'></div>");
            }
            itemsHtml.append("</div>");
        }

        for (DisplayItem s : standaloneItems) {
            String ss = "BORROWED".equals(s.borrowStatus) ? t("Borrowed", "\u5DF2\u501F\u51FA") : t("In Stock", "\u5728\u5E93");
            String sc = "BORROWED".equals(s.borrowStatus) ? "status-borrowed" : "status-instock";
            String sDesc = (s.description != null ? s.description : "") + " " + (s.category != null ? s.category : "");
            itemsHtml.append("<div class='item-group standalone' data-epc='").append(escapeHtml(s.epc))
                     .append("' data-name='").append(escapeHtml(s.name))
                     .append("' data-status='").append(s.borrowStatus)
                     .append("' data-desc='").append(escapeHtml(sDesc)).append("'>")
                     .append("<div class='item-row'>")
                     .append("<span class='item-icon'>\uD83D\uDCCB</span>")
                     .append("<span class='item-name'><a href='/detail?epc=").append(escapeHtml(s.epc)).append("'>")
                     .append(escapeHtml(s.name)).append("</a></span>")
                     .append("<span class='item-epc'>").append(escapeHtml(truncateEpc(s.epc))).append("</span>")
                     .append("<span class='item-info'>");
            StringBuilder sExtra = new StringBuilder();
            if (s.description != null && !s.description.isEmpty()) sExtra.append(" | ").append(escapeHtml(s.description));
            if (s.category != null && !s.category.isEmpty()) sExtra.append(" | ").append(escapeHtml(s.category));
            if (s.itemNumber != null && !s.itemNumber.isEmpty()) sExtra.append(" | " + t("Item No:", "\u8D27\u53F7:")).append(escapeHtml(s.itemNumber));
            itemsHtml.append(sExtra.toString()).append("</span>")
                     .append("<span class='item-status ").append(sc).append("'>").append(ss).append("</span>")
                     .append("</div></div>");
        }

        String emptyHtml = allGroups.isEmpty() && standaloneItems.isEmpty()
                ? "<div class='empty'>\uD83D\uDCE6 " + t("Warehouse is empty", "\u4ED3\u5E93\u4E3A\u7A7A") + "</div>" : "";

        // 完整页面
        String page = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>Simple Invotry - " + t("Warehouse", "\u4ED3\u5E93\u7BA1\u7406") + "</title><style>"
                + "*{box-sizing:border-box;margin:0;padding:0}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;margin:0;padding:0;background:#f5f5f5;color:#333}"
                + ".nav{background:#00897B;padding:0 16px;display:flex;gap:0;overflow:hidden}"
                + ".nav a{color:rgba(255,255,255,.85);text-decoration:none;padding:14px 20px;font-size:15px;font-weight:500;transition:.2s}"
                + ".nav a:hover{background:rgba(255,255,255,.1);color:#fff}.nav a.active{background:rgba(255,255,255,.2);color:#fff}"
                + ".stats{padding:16px;display:flex;flex-wrap:wrap;gap:8px;background:#fff;border-bottom:1px solid #e0e0e0}"
                + ".stat{padding:8px 14px;background:#f0fdfa;border-radius:8px;font-size:13px;color:#555;white-space:nowrap}.stat b{color:#00897B;font-size:16px}"
                + ".toolbar{padding:12px 16px;display:flex;flex-wrap:wrap;gap:8px;align-items:center;background:#fff}"
                + ".toolbar input{flex:1;min-width:150px;padding:10px 14px;border:2px solid #e0e0e0;border-radius:8px;font-size:14px;outline:none;transition:.2s}"
                + ".toolbar input:focus{border-color:#00897B}"
                + ".filter-btn{padding:8px 16px;border:2px solid #e0e0e0;border-radius:8px;background:#fff;font-size:13px;cursor:pointer;color:#555;font-weight:500;transition:.2s}"
                + ".filter-btn:hover{border-color:#00897B;color:#00897B}.filter-on{background:#00897B!important;color:#fff!important;border-color:#00897B!important}"
                + ".items{padding:12px 16px}"
                + ".item-group{background:#fff;border-radius:8px;margin-bottom:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
                + ".item-row{display:flex;flex-wrap:wrap;align-items:center;gap:8px;padding:12px 14px;cursor:pointer;transition:.15s}"
                + ".item-row:hover{background:#f0fdfa}.item-icon{font-size:20px;flex-shrink:0}"
                + ".item-name{font-weight:600;font-size:14px;color:#333;min-width:80px}"
                + ".item-name a{color:#333;text-decoration:none}.item-name a:hover{color:#00897B;text-decoration:underline}"
                + ".item-epc{color:#999;font-size:12px;font-family:monospace;flex:0 0 auto}"
                + ".item-info{color:#888;font-size:12px;flex:1;min-width:80px}"
                + ".item-status{font-size:11px;font-weight:600;padding:3px 10px;border-radius:12px;flex-shrink:0;letter-spacing:.3px}"
                + ".status-instock{background:#E8F5E9;color:#2E7D32}.status-borrowed{background:#FFF3E0;color:#E65100}"
                + ".arrow{color:#bbb;font-size:12px;transition:.2s;flex-shrink:0}.standalone .arrow{display:none}"
                + ".children{border-top:1px solid #f0f0f0}"
                + ".child-row{display:flex;flex-wrap:wrap;align-items:center;gap:8px;padding:10px 14px 10px 48px;transition:.15s}"
                + ".child-row:hover{background:#f8f8f8}.child-icon{font-size:14px;flex-shrink:0}"
                + ".child-row .item-name a{color:#333;text-decoration:none}.child-row .item-name a:hover{color:#00897B;text-decoration:underline}"
                + ".empty{text-align:center;padding:40px 20px;color:#999;font-size:15px}"
                + ".footer{padding:16px;text-align:center;color:#bbb;font-size:12px}"
                + ".info-bar{padding:8px 16px;background:#fff;color:#666;font-size:13px;border-bottom:1px solid #e0e0e0}"
                + "@media(max-width:500px){.item-row,.child-row{font-size:12px}.item-name{min-width:60px}}"
                + "</style><script>"
                + "var currentFilter='all';"
                + "var _BORROWED='" + t("Borrowed", "\u5DF2\u501F\u51FA") + "', _IN_STOCK='" + t("In Stock", "\u5728\u5E93") + "';"
                + "var _S_BOXES='" + t("Boxes:", "\u7BB1\u5B50:") + "', _S_ITEMS='" + t("Items:", "\u7269\u8D44:") + "', _S_INSTOCK='" + t("In Stock:", "\u5728\u5E93:") + "', _S_BORROWED='" + t("Borrowed:", "\u501F\u51FA:") + "', _S_STANDALONE='" + t("Standalone:", "\u72EC\u7ACB:") + "';"
                + "function toggleGroup(epc){var c=document.getElementById('children-'+epc);var a=document.getElementById('arrow-'+epc);if(c){var d=c.style.display;c.style.display=d==='block'?'none':'block';if(a)a.style.transform=d==='block'?'rotate(0deg)':'rotate(180deg)'}}"
                + "function filterItems(){var q=(document.getElementById('searchInput').value||'').toLowerCase();document.querySelectorAll('.item-group').forEach(function(g){var ms=q===''||(g.dataset.name||'').toLowerCase().includes(q)||(g.dataset.epc||'').toLowerCase().includes(q)||(g.dataset.desc||'').toLowerCase().includes(q);var anyChildVisible=false;g.querySelectorAll('.child-row').forEach(function(c){var cm=q===''||(c.dataset.name||'').toLowerCase().includes(q)||(c.dataset.epc||'').toLowerCase().includes(q)||(c.dataset.desc||'').toLowerCase().includes(q);var cf=currentFilter==='all'||c.dataset.status===currentFilter;var visible=cm&&cf;c.style.display=visible?'':'none';if(visible)anyChildVisible=true;if(cm)ms=true;});var fm=currentFilter==='all'||g.dataset.status===currentFilter;g.style.display=(ms&&fm)||anyChildVisible?'':'none';});}"
                + "function setFilter(f){currentFilter=f;document.querySelectorAll('.filter-btn').forEach(function(b){b.classList.remove('filter-on')});document.getElementById('filter-'+f).classList.add('filter-on');filterItems();}"
                + "function refreshData(){fetch('/api/data').then(function(r){return r.json()}).then(function(data){document.getElementById('info-time').textContent=data.timestamp;if(data.stats){document.getElementById('stat-boxes').innerHTML=_S_BOXES+' <b>'+data.stats.boxCount+'</b>';document.getElementById('stat-items').innerHTML=_S_ITEMS+' <b>'+data.stats.itemCount+'</b>';document.getElementById('stat-instock').innerHTML=_S_INSTOCK+' <b>'+data.stats.inStock+'</b>';document.getElementById('stat-borrowed').innerHTML=_S_BORROWED+' <b>'+data.stats.borrowed+'</b>';document.getElementById('stat-standalone').innerHTML=_S_STANDALONE+' <b>'+data.stats.standaloneCount+'</b>';}(data.items||[]).forEach(function(item){var g=document.querySelector('.item-group[data-epc=\\\"'+item.epc+'\"]');if(g){var b=g.querySelector('.item-row>.item-status');if(b){b.textContent=item.borrowStatus==='BORROWED'?_BORROWED:_IN_STOCK;b.className='item-status '+(item.borrowStatus==='BORROWED'?'status-borrowed':'status-instock');g.dataset.status=item.borrowStatus;}(item.children||[]).forEach(function(child){var cr=g.querySelector('.child-row[data-epc=\\\"'+child.epc+'\"]');if(cr){var cb=cr.querySelector('.item-status');if(cb){cb.textContent=child.borrowStatus==='BORROWED'?_BORROWED:_IN_STOCK;cb.className='item-status '+(child.borrowStatus==='BORROWED'?'status-borrowed':'status-instock');cr.dataset.status=child.borrowStatus;}}});}});filterItems();}).catch(function(e){console.log('Refresh error:',e);});}"
                + "setInterval(refreshData,10000);"
                + "</script></head><body>"
                + navHtml("warehouse")
                + "<div class='stats'>"
                + "<span class='stat' id='stat-boxes'>\uD83D\uDCE6" + t("Boxes:", "\u7BB1\u5B50:") + " <b>" + boxCount + "</b></span>"
                + "<span class='stat' id='stat-items'>\uD83D\uDCC4" + t("Items:", "\u7269\u8D44:") + " <b>" + itemCount + "</b></span>"
                + "<span class='stat' id='stat-instock' style='background:#E8F5E9'>\u2705" + t("In Stock:", "\u5728\u5E93:") + " <b style='color:#2E7D32'>" + inStock + "</b></span>"
                + "<span class='stat' id='stat-borrowed' style='background:#FFF3E0'>\uD83D\uDCE4" + t("Borrowed:", "\u501F\u51FA:") + " <b style='color:#E65100'>" + borrowed + "</b></span>"
                + "<span class='stat' id='stat-standalone'>\uD83D\uDCCB" + t("Standalone:", "\u72EC\u7ACB:") + " <b>" + standaloneCount + "</b></span>"
                + "</div>"
                + "<div class='toolbar'>"
                + "<input type='text' id='searchInput' placeholder='" + t("\uD83D\uDD0D Search name, TID, description, category, item No, shelf, room...", "\uD83D\uDD0D \u641C\u7D22\u540D\u79F0\u3001TID\u3001\u63CF\u8FF0\u3001\u79CD\u7C7B\u3001\u8D27\u53F7\u3001\u8D27\u67B6\u3001\u623F\u95F4...") + "' oninput='filterItems()'>"
                + "<button class='filter-btn filter-on' onclick='setFilter(\"all\")' id='filter-all'>" + t("All", "\u5168\u90E8") + "</button>"
                + "<button class='filter-btn' onclick='setFilter(\"IN_STOCK\")' id='filter-IN_STOCK'>" + t("In Stock", "\u5728\u5E93") + "</button>"
                + "<button class='filter-btn' onclick='setFilter(\"BORROWED\")' id='filter-BORROWED'>" + t("Borrowed", "\u501F\u51FA") + "</button>"
                + "</div>"
                + "<div class='info-bar'>\uD83D\uDD50 <span id='info-time'>" + now + "</span> | " + t("Data auto-refreshes every 10s | Tap item name for details", "\u6570\u636E\u6BCF10\u79D2\u81EA\u52A8\u66F4\u65B0 | \u70B9\u51FB\u7269\u54C1\u540D\u79F0\u67E5\u770B\u8BE6\u60C5") + "</div>"
                + "<div class='items' id='itemsContainer'>"
                + itemsHtml.toString()
                + emptyHtml
                + "</div>"
                + "<div class='footer'>Simple Invotry - " + t("Warehouse", "\u4ED3\u5E93\u7BA1\u7406") + "</div>"
                + "</body></html>";

        return page;
    }

    // ==================== 物品详情页面 ====================

    /** 构建物品详情页 */
    private String buildDetailPage(String epc) {
        if (epc == null || epc.isEmpty() || dbHelper == null) {
            return errorPage("\u26A0\uFE0F " + (dbHelper == null ? t("Database unavailable", "\u6570\u636E\u5E93\u4E0D\u53EF\u7528") : t("Missing item EPC", "\u7F3A\u5C11\u7269\u54C1\u7F16\u53F7")));
        }

        String itemName = t("Unknown Item", "\u672A\u77E5\u7269\u54C1"), fullEpc = epc;
        String description = "-", typeLabel = t("Unknown Type", "\u672A\u77E5\u7C7B\u578B");
        String category = "-", itemNumber = "-", shelf = "-", room = "-";
        String borrowStatus = "IN_STOCK", createdAtTime = "-";
        String extraHtml = "";
        String historyHtml = buildHistoryHtml(epc);

        // \u5C1D\u8BD5\u4F5C\u4E3A Box \u67E5\u627E
        BoxInfo box = dbHelper.getBoxByEpc(epc);
        if (box != null) {
            itemName = box.toString();
            fullEpc = box.epc;
            description = (box.description != null && !box.description.isEmpty()) ? box.description : "-";
            typeLabel = "\uD83D\uDCE6 " + t("Box", "\u7BB1\u5B50") + " (Box)";
            borrowStatus = dbHelper.getItemBorrowStatus(box.epc);
            createdAtTime = (box.createdAt != null && !box.createdAt.isEmpty()) ? box.createdAt : "-";

            StockInInfo si = dbHelper.getLatestStockIn(epc);
            if (si != null) {
                if (si.description != null && !si.description.isEmpty()) description = si.description;
                if (si.category != null) category = si.category;
                if (si.itemNumber != null) itemNumber = si.itemNumber;
                if (si.shelf != null) shelf = si.shelf;
                if (si.room != null) room = si.room;
                if (si.timestamp != null) createdAtTime = si.timestamp;
            }

            List<ContentInfo> contents = dbHelper.getContentsByBoxEpc(box.epc);
            if (!contents.isEmpty()) {
                StringBuilder cb = new StringBuilder();
                cb.append("<h3 style='margin:20px 0 10px;font-size:16px;color:#333'>\uD83D\uDCC4 " + t("Child Items", "\u5B50\u9879\u7269\u8D44") + " (").append(contents.size()).append(")</h3>");
                for (ContentInfo c : contents) {
                    String cs = dbHelper.getItemBorrowStatus(c.epc);
                    String csc = "BORROWED".equals(cs) ? "status-borrowed" : "status-instock";
                    cb.append("<div style='display:flex;align-items:center;gap:8px;padding:8px 0;border-bottom:1px solid #f0f0f0'>")
                      .append("<a href='/detail?epc=").append(escapeHtml(c.epc)).append("' style='color:#00897B;text-decoration:none;font-weight:500'>")
                      .append(escapeHtml(c.toString())).append("</a>")
                      .append("<span style='color:#999;font-size:12px'>").append(escapeHtml(c.epc)).append("</span>")
                      .append("<span class='item-status ").append(csc).append("'>").append("BORROWED".equals(cs) ? t("Borrowed", "\u5DF2\u501F\u51FA") : t("In Stock", "\u5728\u5E93")).append("</span>")
                      .append("</div>");
                }
                extraHtml = cb.toString();
            }
            return detailPageHtml(itemName, fullEpc, description, typeLabel, category, itemNumber, shelf, room, borrowStatus, createdAtTime, extraHtml, historyHtml);
        }

        // \u5C1D\u8BD5\u4F5C\u4E3A Content \u67E5\u627E
        BoxInfo parentBox = dbHelper.getBoxForContent(epc);
        if (parentBox != null) {
            List<ContentInfo> contents = dbHelper.getContentsByBoxEpc(parentBox.epc);
            for (ContentInfo c : contents) {
                if (c.epc.equalsIgnoreCase(epc)) {
                    itemName = c.toString();
                    fullEpc = c.epc;
                    description = (c.description != null && !c.description.isEmpty()) ? c.description : "-";
                    typeLabel = "\uD83D\uDCC4 " + t("Content", "\u5185\u5BB9\u7269") + " (Content)";
                    borrowStatus = dbHelper.getItemBorrowStatus(c.epc);
                    createdAtTime = (c.createdAt != null && !c.createdAt.isEmpty()) ? c.createdAt : "-";

                    StockInInfo si = dbHelper.getLatestStockIn(epc);
                    if (si != null) {
                        if (si.description != null && !si.description.isEmpty()) description = si.description;
                        if (si.category != null) category = si.category;
                        if (si.itemNumber != null) itemNumber = si.itemNumber;
                        if (si.shelf != null) shelf = si.shelf;
                        if (si.room != null) room = si.room;
                        if (si.timestamp != null) createdAtTime = si.timestamp;
                    }

                    extraHtml = "<h3 style='margin:20px 0 10px;font-size:16px;color:#333'>\uD83D\uDCE6 " + t("Parent Box", "\u6240\u5C5E\u7BB1\u5B50") + "</h3>"
                        + "<div style='display:flex;align-items:center;gap:8px;padding:8px 0'>"
                        + "<a href='/detail?epc=" + escapeHtml(parentBox.epc) + "' style='color:#00897B;text-decoration:none;font-weight:500'>"
                        + escapeHtml(parentBox.toString()) + "</a>"
                        + "<span style='color:#999;font-size:12px'>" + escapeHtml(parentBox.epc) + "</span></div>";
                    return detailPageHtml(itemName, fullEpc, description, typeLabel, category, itemNumber, shelf, room, borrowStatus, createdAtTime, extraHtml, historyHtml);
                }
            }
        }

        // \u5C1D\u8BD5\u4F5C\u4E3A Standalone \u67E5\u627E
        StockInInfo si = dbHelper.getLatestStockIn(epc);
        if (si != null) {
            itemName = si.toString();
            fullEpc = si.epc;
            description = (si.description != null && !si.description.isEmpty()) ? si.description : "-";
            typeLabel = "\uD83D\uDCCB " + t("Standalone Item", "\u72EC\u7ACB\u7269\u54C1") + " (Standalone)";
            borrowStatus = dbHelper.getItemBorrowStatus(si.epc);
            createdAtTime = (si.timestamp != null) ? si.timestamp : "-";
            if (si.category != null) category = si.category;
            if (si.itemNumber != null) itemNumber = si.itemNumber;
            if (si.shelf != null) shelf = si.shelf;
            if (si.room != null) room = si.room;
            return detailPageHtml(itemName, fullEpc, description, typeLabel, category, itemNumber, shelf, room, borrowStatus, createdAtTime, "", historyHtml);
        }

        // \u672A\u627E\u5230
        return errorPage("\u26A0\uFE0F " + t("Item not found for EPC", "\u672A\u627E\u5230 EPC \u4E3A") + " " + escapeHtml(epc));
    }

    private String detailPageHtml(String name, String epc, String desc, String typeLabel,
                                   String cat, String itemNo, String sh, String rm,
                                   String status, String createdAtTime, String extra, String historyHtml) {
        String st = "BORROWED".equals(status) ? t("Borrowed", "\u5DF2\u501F\u51FA") : t("In Stock", "\u5728\u5E93");
        String sc = "BORROWED".equals(status) ? "status-borrowed" : "status-instock";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>" + escapeHtml(name) + " - " + t("Item Details", "\u7269\u54C1\u8BE6\u60C5") + "</title>"
            + "<style>*{box-sizing:border-box;margin:0;padding:0}"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;margin:0;padding:0;background:#f5f5f5;color:#333}"
            + ".nav{background:#00897B;padding:0 16px;display:flex;gap:0;overflow:hidden}"
            + ".nav a{color:rgba(255,255,255,.85);text-decoration:none;padding:14px 20px;font-size:15px;font-weight:500;transition:.2s}"
            + ".nav a:hover{background:rgba(255,255,255,.1);color:#fff}.nav a.active{background:rgba(255,255,255,.2);color:#fff}"
            + ".back-link{display:block;padding:12px 16px;background:#fff;border-bottom:1px solid #e0e0e0}"
            + ".back-link a{color:#00897B;text-decoration:none;font-size:14px;font-weight:500}"
            + ".back-link a:hover{text-decoration:underline}"
            + ".card{background:#fff;margin:16px;border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.08)}"
            + ".card h2{padding:20px 20px 6px;font-size:20px}.card .st{padding:0 20px 16px}"
            + "table{width:100%;border-collapse:collapse}"
            + "td{padding:12px 16px;border-bottom:1px solid #f5f5f5;font-size:14px}"
            + "td:first-child{color:#888;width:80px;font-weight:500;white-space:nowrap}"
            + "td:last-child{color:#333;word-break:break-all}"
            + ".item-status{display:inline-block;font-size:12px;font-weight:600;padding:4px 12px;border-radius:12px}"
            + ".status-instock{background:#E8F5E9;color:#2E7D32}.status-borrowed{background:#FFF3E0;color:#E65100}"
            + ".history-item{display:flex;align-items:center;gap:8px;padding:10px 0;border-bottom:1px solid #f0f0f0}"
            + ".history-item:last-child{border-bottom:none}"
            + ".h-type{font-size:12px;font-weight:600;padding:2px 8px;border-radius:4px;white-space:nowrap;flex-shrink:0}"
            + ".h-type-stockin{background:#E3F2FD;color:#1565C0}.h-type-borrow{background:#FFF3E0;color:#E65100}.h-type-return{background:#E8F5E9;color:#2E7D32}"
            + ".h-name{color:#333;font-size:13px;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
            + ".h-time{color:#bbb;font-size:12px;white-space:nowrap;flex-shrink:0}"
            + ".footer{text-align:center;padding:16px}.footer a{color:#00897B;text-decoration:none;font-size:14px}"
            + "@media(max-width:500px){td{font-size:13px;padding:10px 12px}}</style></head><body>"
            + navHtml("warehouse")
            + "<div class='back-link'><a href='/'>\u2190 " + t("Back to Warehouse", "\u8FD4\u56DE\u4ED3\u5E93") + "</a></div>"
            + "<div class='card'><h2>" + escapeHtml(name) + "</h2>"
            + "<div class='st'><span class='item-status " + sc + "'>" + st + "</span></div>"
            + "<table><tr><td>TID</td><td style='font-family:monospace;font-size:12px'>" + escapeHtml(epc) + "</td></tr>"
            + "<tr><td>" + t("Description", "\u63CF\u8FF0") + "</td><td>" + escapeHtml(desc) + "</td></tr>"
            + "<tr><td>" + t("Type", "\u7C7B\u578B") + "</td><td>" + typeLabel + "</td></tr>"
            + "<tr><td>" + t("Category", "\u79CD\u7C7B") + "</td><td>" + escapeHtml(cat) + "</td></tr>"
            + "<tr><td>" + t("Item No", "\u8D27\u53F7") + "</td><td>" + escapeHtml(itemNo) + "</td></tr>"
            + "<tr><td>" + t("Shelf", "\u8D27\u67B6") + "</td><td>" + escapeHtml(sh) + "</td></tr>"
            + "<tr><td>" + t("Room", "\u623F\u95F4") + "</td><td>" + escapeHtml(rm) + "</td></tr>"
            + "<tr><td>" + t("Stock In Time", "\u5165\u5E93\u65F6\u95F4") + "</td><td>" + (createdAtTime.length() > 10 ? createdAtTime : createdAtTime) + "</td></tr>"
            + "</table>" + (extra.isEmpty() ? "" : "<div style='padding:0 16px 16px'>" + extra + "</div>") + "</div>"
            + (historyHtml.isEmpty() ? "" : historyHtml)
            + "<div class='footer'><a href='/'>\u2190 " + t("Back to Warehouse", "\u8FD4\u56DE\u4ED3\u5E93") + "</a></div>"
            + "</body></html>";
    }

    private String errorPage(String msg) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><title>" + t("Error", "\u9519\u8BEF") + "</title>"
            + "<style>body{font-family:sans-serif;padding:40px;text-align:center;color:#666}"
            + "a{color:#00897B}#back{display:inline-block;margin:20px;padding:10px 20px;background:#00897B;color:#fff;text-decoration:none;border-radius:8px}</style></head><body>"
            + "<h2>" + msg + "</h2>"
            + "<a id='back' href='/'>\u2190 " + t("Back to Warehouse", "\u8FD4\u56DE\u4ED3\u5E93") + "</a></body></html>";
    }
    
    // ==================== \u5386\u53F2\u8BB0\u5F55 ====================
    
    /** \u6784\u5EFA\u5386\u53F2\u8BB0\u5F55HTML\uff0c\u5305\u542B\u5165\u5E93\u548C\u501F\u51FA/\u5F52\u8FD8\u8BB0\u5F55 */
    private String buildHistoryHtml(String epc) {
        if (epc == null || epc.isEmpty() || dbHelper == null) return "";
    
        List<Object> historyList = new ArrayList<>();
        try {
            historyList.addAll(dbHelper.getStockInHistory(epc));
            historyList.addAll(dbHelper.getCheckoutHistoryForItem(epc));
        } catch (Exception e) {
            Log.e(TAG, "\u52A0\u8F7D\u5386\u53F2\u8BB0\u5F55\u5931\u8D25", e);
        }
        if (historyList.isEmpty()) return "";
    
        // \u6309\u65F6\u95F4\u5012\u5E8F\u6392\u5217
        Collections.sort(historyList, (a, b) -> {
            String tsA = (a instanceof StockInInfo) ? ((StockInInfo) a).timestamp : ((CheckoutLogInfo) a).timestamp;
            String tsB = (b instanceof StockInInfo) ? ((StockInInfo) b).timestamp : ((CheckoutLogInfo) b).timestamp;
            try {
                return Long.compare(Long.parseLong(tsB), Long.parseLong(tsA));
            } catch (Exception e) {
                return 0;
            }
        });
    
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder();
    
        for (Object record : historyList) {
            if (record instanceof StockInInfo) {
                StockInInfo si = (StockInInfo) record;
                String typeLabel;
                switch (si.type) {
                    case "BOX": typeLabel = t("Stock In(Box)", "\u5165\u5E93(\u7BB1\u5B50)"); break;
                    case "CONTENT": typeLabel = t("Stock In(Content)", "\u5165\u5E93(\u5185\u5BB9)"); break;
                    default: typeLabel = t("Stock In(Standalone)", "\u5165\u5E93(\u72EC\u7ACB)"); break;
                }
                String timeStr;
                try { timeStr = sdf.format(new Date(Long.parseLong(si.timestamp))); }
                catch (Exception e) { timeStr = si.timestamp != null ? si.timestamp : ""; }
    
                sb.append("<div class='history-item'>")
                  .append("<span class='h-type h-type-stockin'>").append(typeLabel).append("</span>")
                  .append("<span class='h-name'>").append(escapeHtml(si.toString())).append("</span>")
                  .append("<span class='h-time'>").append(timeStr).append("</span>")
                  .append("</div>");
            } else if (record instanceof CheckoutLogInfo) {
                CheckoutLogInfo cl = (CheckoutLogInfo) record;
                boolean isBorrow = "BORROW".equals(cl.status);
                String action = isBorrow ? t("Borrow", "\u501F\u51FA") : t("Return", "\u5F52\u8FD8");
                String typeClass = isBorrow ? "h-type-borrow" : "h-type-return";
                String timeStr;
                try { timeStr = sdf.format(new Date(Long.parseLong(cl.timestamp))); }
                catch (Exception e) { timeStr = cl.timestamp != null ? cl.timestamp : ""; }
    
                sb.append("<div class='history-item'>")
                  .append("<span class='h-type ").append(typeClass).append("'>").append(action).append("</span>")
                  .append("<span class='h-name'>").append(escapeHtml(cl.studentId)).append("</span>")
                  .append("<span class='h-time'>").append(timeStr).append("</span>")
                  .append("</div>");
            }
        }
    
        return "<div class='card' style='margin-top:0'><h3 style='padding:16px 20px 4px;font-size:16px;color:#333'>\uD83D\uDCCB " + t("Operation History", "\u64CD\u4F5C\u5386\u53F2") + "</h3>"
             + "<div style='padding:0 16px 8px'>"
             + sb.toString()
             + "</div></div>";
    }
    
    // ==================== JSON \u6570\u636E\u63A5\u53E3 ====================

    /** \u6784\u5EFA JSON \u6570\u636E\u63A5\u53E3 */
    private String buildDataJson() {
        StringBuilder sb = new StringBuilder();
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        int boxCount = 0, itemCount = 0, inStock = 0, borrowed = 0, standaloneCount = 0;
        sb.append("{\"timestamp\":\"").append(jsonEscape(now)).append("\"");

        if (dbHelper != null) {
            try {
                List<BoxInfo> boxes = dbHelper.getAllBoxes();
                for (BoxInfo box : boxes) {
                    boxCount++; itemCount++;
                    if ("BORROWED".equals(dbHelper.getItemBorrowStatus(box.epc))) borrowed++; else inStock++;
                    List<ContentInfo> contents = dbHelper.getContentsByBoxEpc(box.epc);
                    itemCount += contents.size();
                    for (ContentInfo c : contents) {
                        if ("BORROWED".equals(dbHelper.getItemBorrowStatus(c.epc))) borrowed++; else inStock++;
                    }
                }
                List<StockInInfo> standalones = dbHelper.getStandaloneItems();
                standaloneCount = standalones.size(); itemCount += standalones.size();
                for (StockInInfo si : standalones) {
                    if ("BORROWED".equals(dbHelper.getItemBorrowStatus(si.epc))) borrowed++; else inStock++;
                }
            } catch (Exception e) { Log.e(TAG, "JSON\u6570\u636E\u751F\u6210\u5931\u8D25", e); }
        }

        sb.append(",\"stats\":{")
          .append("\"boxCount\":").append(boxCount)
          .append(",\"itemCount\":").append(itemCount)
          .append(",\"inStock\":").append(inStock)
          .append(",\"borrowed\":").append(borrowed)
          .append(",\"standaloneCount\":").append(standaloneCount)
          .append("}");

        sb.append(",\"items\":[");
        boolean first = true;
        if (dbHelper != null) {
            try {
                for (BoxInfo box : dbHelper.getAllBoxes()) {
                    if (!first) sb.append(","); first = false;
                    String boxStatus = dbHelper.getItemBorrowStatus(box.epc);
                    StockInInfo si = dbHelper.getLatestStockIn(box.epc);
                    sb.append("{\"epc\":\"").append(jsonEscape(box.epc)).append("\"")
                      .append(",\"name\":\"").append(jsonEscape(box.toString())).append("\"")
                      .append(",\"type\":\"BOX\"")
                      .append(",\"borrowStatus\":\"").append(jsonEscape(boxStatus)).append("\"")
                      .append(",\"description\":\"").append(jsonEscape(si != null && si.description != null ? si.description : "")).append("\"")
                      .append(",\"category\":\"").append(jsonEscape(si != null && si.category != null ? si.category : "")).append("\"")
                      .append(",\"itemNumber\":\"").append(jsonEscape(si != null && si.itemNumber != null ? si.itemNumber : "")).append("\"")
                      .append(",\"shelf\":\"").append(jsonEscape(si != null && si.shelf != null ? si.shelf : "")).append("\"")
                      .append(",\"room\":\"").append(jsonEscape(si != null && si.room != null ? si.room : "")).append("\"");
                    List<ContentInfo> contents = dbHelper.getContentsByBoxEpc(box.epc);
                    sb.append(",\"children\":[");
                    for (int i = 0; i < contents.size(); i++) {
                        if (i > 0) sb.append(",");
                        ContentInfo c = contents.get(i);
                        String cStatus = dbHelper.getItemBorrowStatus(c.epc);
                        StockInInfo cSi = dbHelper.getLatestStockIn(c.epc);
                        sb.append("{\"epc\":\"").append(jsonEscape(c.epc)).append("\"")
                          .append(",\"name\":\"").append(jsonEscape(c.toString())).append("\"")
                          .append(",\"type\":\"CONTENT\"")
                          .append(",\"borrowStatus\":\"").append(jsonEscape(cStatus)).append("\"")
                          .append(",\"description\":\"").append(jsonEscape(cSi != null && cSi.description != null ? cSi.description : "")).append("\"")
                          .append("}");
                    }
                    sb.append("]}");
                }
                for (StockInInfo si : dbHelper.getStandaloneItems()) {
                    if (!first) sb.append(","); first = false;
                    String sStatus = dbHelper.getItemBorrowStatus(si.epc);
                    sb.append("{\"epc\":\"").append(jsonEscape(si.epc)).append("\"")
                      .append(",\"name\":\"").append(jsonEscape(si.toString())).append("\"")
                      .append(",\"type\":\"STANDALONE\"")
                      .append(",\"borrowStatus\":\"").append(jsonEscape(sStatus)).append("\"")
                      .append(",\"description\":\"").append(jsonEscape(si.description != null ? si.description : "")).append("\"")
                      .append(",\"category\":\"").append(jsonEscape(si.category != null ? si.category : "")).append("\"")
                      .append(",\"itemNumber\":\"").append(jsonEscape(si.itemNumber != null ? si.itemNumber : "")).append("\"")
                      .append(",\"shelf\":\"").append(jsonEscape(si.shelf != null ? si.shelf : "")).append("\"")
                      .append(",\"room\":\"").append(jsonEscape(si.room != null ? si.room : "")).append("\"")
                      .append(",\"children\":[]}")
                      ;
                }
            } catch (Exception e) { Log.e(TAG, "JSON\u6570\u636E\u751F\u6210\u5931\u8D25", e); }
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String na(String value) {
        return (value != null && !value.isEmpty()) ? value : "-";
    }

    private static String t(String en, String zh) {
        return isChinese ? zh : en;
    }

    // ==================== 日志页面 ====================

    /** 构建操作日志页面 */
    private String buildLogsPage() {
        String logsHtml = logProvider != null
                ? logProvider.getLogHtmlContent()
                : "<tr><td colspan='3' style='text-align:center;color:#999;padding:20px'>" + t("No logs yet", "\u6682\u65E0\u65E5\u5FD7") + "</td></tr>";
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        return "<!DOCTYPE html><html><head>"
                + "<meta charset='utf-8'>"
                + "<meta http-equiv='refresh' content='5'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + t("Simple Invotry - Operation Logs", "Simple Invotry - \u64CD\u4F5C\u65E5\u5FD7") + "</title>"
                + "<meta name='language' content='" + (isChinese ? "zh" : "en") + "'>"
                + "<style>"
                + "*{box-sizing:border-box;margin:0;padding:0}"
                + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
                + "margin:0;padding:0;background:#f5f5f5;color:#333}"
                + ".nav{background:#00897B;padding:0 16px;display:flex;gap:0;overflow:hidden}"
                + ".nav a{color:rgba(255,255,255,.85);text-decoration:none;padding:14px 20px;"
                + "font-size:15px;font-weight:500;transition:.2s}"
                + ".nav a:hover{background:rgba(255,255,255,.1);color:#fff}"
                + ".nav a.active{background:rgba(255,255,255,.2);color:#fff}"
                + ".content{padding:16px}"
                + "h2{font-size:20px;margin-bottom:6px;display:flex;align-items:center;gap:8px}"
                + ".info{color:#666;font-size:13px;margin-bottom:16px;padding:8px 12px;"
                + "background:#fff;border-radius:8px;box-shadow:0 1px 2px rgba(0,0,0,.08)}"
                + "table{width:100%;border-collapse:collapse;background:#fff;"
                + "border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.1)}"
                + "th{background:#00897B;color:#fff;padding:10px 12px;text-align:left;font-size:13px;font-weight:600}"
                + "td{padding:8px 12px;border-bottom:1px solid #f0f0f0;font-size:13px;vertical-align:middle}"
                + "tr:last-child td{border-bottom:none}"
                + "tr:hover{background:#f0fdfa}"
                + ".type{display:inline-block;padding:2px 8px;border-radius:4px;color:#fff;"
                + "font-size:11px;font-weight:600;letter-spacing:.3px;white-space:nowrap}"
                + ".type-borrow,.type-\\u501F\\u51FA{background:#FB8C00}"
                + ".type-return,.type-\\u5F52\\u8FD8{background:#43A047}"
                + ".type-stockin,.type-\\u5165\\u5E93{background:#1E88E5}"
                + ".type-kitting{background:#8E24AA}"
                + ".type-search,.type-\\u67E5\\u8BE2{background:#546E7A}"
                + ".type-export,.type-\\u5BFC\\u51FA\\u6587\\u4EF6{background:#00ACC1}"
                + ".type-tag,.type-RFID\\u6807\\u7B7E{background:#78909C}"
                + ".type-other{background:#757575}"
                + ".ts{color:#999;font-size:12px;white-space:nowrap}"
                + ".badge{display:inline-block;background:#E8F5E9;color:#2E7D32;"
                + "border-radius:12px;padding:2px 10px;font-size:12px;font-weight:500}"
                + ".footer{margin-top:16px;color:#aaa;font-size:12px;text-align:center}"
                + "@media(max-width:600px){th,td{padding:6px 8px;font-size:12px}}"
                + "</style></head><body>"
                + navHtml("logs")
                + "<div class='content'>"
                + "<h2>\uD83D\uDCCB " + t("Simple Invotry Operation Logs", "Simple Invotry 操作日志") + "</h2>"
                + "<div class='info'>"
                + "\uD83D\uDD50 " + now
                + " &nbsp;|&nbsp; " + t("\uD83D\uDCF6 Page auto-refreshes every 5 seconds", "\uD83D\uDCF6 页面每5秒自动刷新")
                + " &nbsp;|&nbsp; <span class='badge'>" + t("Real-time Wireless Logs", "实时无线日志") + "</span>"
                + "</div>"
                + "<table><thead><tr>"
                + "<th style='width:160px'>" + t("Time", "时间") + "</th>"
                + "<th style='width:90px'>" + t("Type", "类型") + "</th>"
                + "<th>" + t("Content", "操作内容") + "</th>"
                + "</tr></thead><tbody>"
                + logsHtml
                + "</tbody></table>"
                + "<div class='footer'>" + t("Simple Invotry - Operation Logs", "Simple Invotry - 操作日志") + "</div>"
                + "</div>"
                + "</body></html>";
    }

    /** 将 OperationLogManager 中的日志条目转换为HTML表格行 */
    public static String buildLogRows(List<OperationLogManager.LogEntry> logs) {
        if (logs == null || logs.isEmpty()) {
            return "<tr><td colspan='3' style='text-align:center;color:#999;padding:24px'>"
                    + t("No log records", "\u6682\u65E0\u65E5\u5FD7\u8BB0\u5F55") + "</td></tr>";
        }
        StringBuilder sb = new StringBuilder();
        // 倒序显示（最新的在前）
        for (int i = logs.size() - 1; i >= 0; i--) {
            OperationLogManager.LogEntry entry = logs.get(i);
            String typeClass = getTypeCssClass(entry.type);
            sb.append("<tr>")
              .append("<td class='ts'>").append(escapeHtml(entry.timestamp)).append("</td>")
              .append("<td><span class='type ").append(typeClass).append("'>")
              .append(escapeHtml(translateType(entry.type))).append("</span></td>")
              .append("<td>").append(escapeHtml(translateMessage(entry.message))).append("</td>")
              .append("</tr>");
        }
        return sb.toString();
    }

    private static String getTypeCssClass(String type) {
        if (type == null) return "type-other";
        if (type.contains("借出") || type.contains("归还") || type.contains("Borrow") || type.contains("Return")) {
            return type.contains("借出") || type.contains("Borrow") ? "type-borrow" : "type-return";
        }
        if (type.contains("入库") || type.contains("StockIn")) return "type-stockin";
        if (type.contains("Kitting")) return "type-kitting";
        if (type.contains("查询") || type.contains("Search")) return "type-search";
        if (type.contains("导出文件")) return "type-export";
        if (type.contains("RFID标签")) return "type-tag";
        return "type-other";
    }

    /** 将日志类型从中文翻译为英文 */
    public static String translateType(String type) {
        if (type == null || isChinese) return type;
        if (type.contains("借出") || type.contains("Borrow")) return "Borrow";
        if (type.contains("归还") || type.contains("Return")) return "Return";
        if (type.contains("入库") || type.contains("StockIn")) return "Stock In";
        if (type.contains("Kitting")) return "Kitting";
        if (type.contains("查询") || type.contains("Search")) return "Search";
        if (type.contains("导出文件")) return "Export";
        if (type.contains("RFID标签")) return "RFID Tag";
        return type;
    }

    /** 将日志消息中的中文标签翻译为英文（仅英文模式下生效） */
    public static String translateMessage(String msg) {
        if (msg == null || isChinese) return msg;
        String result = msg;
        result = result.replace("注册为Box", "Registered as Box");
        result = result.replace("注册为Content", "Registered as Content");
        result = result.replace("注册为Standalone", "Registered as Standalone");
        result = result.replace("分配Box", "Assigned Box");
        result = result.replace("添加Content到Box", "Added Content to Box");
        result = result.replace(" 简称:", " ShortName:");
        result = result.replace(" 描述:", " Description:");
        result = result.replace(" 归属Box:", " Belongs to Box:");
        result = result.replace(" | 操作人: ", " | Operator: ");
        return result;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    private static String truncateEpc(String epc) {
        if (epc == null) return "";
        return epc.length() > 12 ? epc.substring(0, 12) + "..." : epc;
    }

    /** URL 解码（简易实现，只处理 %xx 和 +） */
    private static String urlDecode(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+') {
                sb.append(' ');
            } else if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16);
                int lo = Character.digit(s.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    sb.append((char) ((hi << 4) | lo));
                    i += 2;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
