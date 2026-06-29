# 简库 - UHF RFID 智能库存管理系统

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)](https://www.android.com)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen)](https://developer.android.com/about/versions/oreo)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.4.0-orange)](https://github.com/ox-x/Sample-invotory/releases)

基于 UHF RFID 技术的安卓智能库存管理应用，支持资产入库、借还管理、仓库盘点、智能搜索及 Excel 报表导出。

---

## 功能概览

| 功能 | 描述 |
|------|------|
| **📦 入库管理** | 录入资产信息（品类、货号、货架、房间），支持多照片拍摄，关联 RFID 标签 |
| **🔄 借还管理** | 通过学生证/工号确认身份，RFID 批量扫描借出/归还，自动审计与缺失预警 |
| **🏬 仓库管理** | 总览所有资产库存状态（在库/借出），支持列表/网格双视图，模糊搜索，照片预览 |
| **📋 装箱管理** | 建立 RFID 容器与子物品的层级关系，支持拍照记录 |
| **🔍 智能搜索** | 按短码、EPC/TID、描述进行模糊搜索，展示物品状态与借还历史 |
| **📊 Excel 导出** | 一键导出仓库全部库存数据（含照片路径）为 `.xls` 文件，支持 `jxl` 和 `POI` 双引擎 |
| **⚙️ UHF 配置** | 功率调节、读写标签、锁定/解锁、标签定位、雷达扫描、固件升级 |

## 技术栈

- **语言**: Java
- **最低 API**: 26 (Android 8.0)
- **目标 API**: 33
- **UI**: ViewBinding + Material 3 主题
- **数据库**: SQLite (Room 未使用，手写 SQLiteOpenHelper，含外键与索引)
- **RFID SDK**: `DeviceAPI_ver20250209_release.aar`（支持 UHF 读写、定位、雷达等功能）
- **Excel**: `jxl.jar` + `Apache POI 3.12`
- **网络库**: `xUtils-2.5.5`
- **构建**: Gradle + Android Studio

## 数据库结构

| 表 | 用途 |
|----|------|
| `boxes` | RFID 容器（箱子/父级资产） |
| `contents` | 容器内的子物品 |
| `checkout_logs` | 借还记录（含学生信息、借出状态、缺失物品） |
| `stock_ins` | 入库资产（含品类、货号、货架、房间、多照片） |

## 项目结构

```
app/src/main/java/com/example/uhf/
├── activity/           # 主 Activity（Splash, UHFMain）
├── fragment/           # 功能碎片（Dashboard, Checkout, StockIn, Warehouse, Kitting, Search 等）
├── db/                 # SQLite 数据库（DatabaseHelper + 数据模型）
├── tools/              # 工具类（Excel 导出、字符串处理、UI 辅助）
├── view/               # 自定义视图（雷达、圆形滑块等）
└── widget/             # 自定义 ViewPager
```

## 快速开始

1. 克隆仓库
   ```bash
   git clone https://github.com/ox-x/Sample-invotory.git
   ```

2. 用 Android Studio 打开项目

3. 连接 UHF RFID 硬件设备或在模拟器中运行（仅调试 UI）

4. 构建并运行
   ```bash
   ./gradlew assembleDebug
   ```
   APK 生成位置: `app/build/outputs/apk/debug/简库_v1.4.0.apk`

## 版本管理

版本号格式：`主版本.次版本.修订号`

- `versionCode` = 主版本 × 100000 + 次版本 × 1000 + 修订号
- 修改 `app/build.gradle` 中的 `versionMajor` / `versionMinor` / `versionPatch` 即可自动更新

## 截图

| 首页仪表盘 | 借还管理 | 仓库总览 |
|:---:|:---:|:---:|
| ![Dashboard](screenshots/dashboard.png) | ![Checkout](screenshots/checkout.png) | ![Warehouse](screenshots/warehouse.png) |


