因为原作者不更新了所以自己尝试写了一下修修bug，不懂安卓乱写的，有问题请先自己解决

---
# DD_Monitor-android-kotlin

![GitHub all releases](https://img.shields.io/github/downloads/congHu/DD_Monitor-android-kotlin/total)

## 版本特性
- 支持Android7.0以上。
- 支持直播间id添加UP主。
- 支持拖拽UP主卡片添加到直播窗口，支持窗口拖拽交换。
- 支持横屏锁定。
- 支持定时睡眠。
- 支持16+11种布局方式。

## 使用须知
- 使用时请注意宽带网速、流量消耗、电池电量、机身发热、系统卡顿等软硬件环境问题。
- 本软件仅读取公开API数据，不涉及账号登录，欢迎查看源码进行监督。因此，本软件不支持弹幕互动、直播打赏等功能，若要使用请前往原版B站APP。
- 直播流、UP主信息、以及个人公开的关注列表数据来自B站公开API，最终解释权归B站所有。

## 使用方式
- 点击右上角“UP”按钮添加UP主，长按拖动到播放器窗口内。
- 点击右上角"布局"按钮切换窗口布局。
- 长按UP主卡片，然后拖动到窗口中。
- 点击UP主卡片、或者点击下方UP主名字，弹出菜单选项，可跳转至UP主的直播间进行互动。

## TODO&BUG
- [x] 音量的保存，以及交换窗口后同时交换音量大小
- [x] 弹幕字体、视窗宽高
- [x] 同传弹幕过滤
- [x] 双击窗口全屏
- [x] 前台开播提醒功能
- [x] uid导入公开关注列表
- [x] 显示SC弹幕
- [x] 点击弹出删除弹幕选项（删除片哥）
- [x] 扫码PC分享二维码

## 给开发者买杯奶茶
[qrcode.png](qrcode.png)

## 开源依赖
- [https://github.com/square/okhttp](https://github.com/square/okhttp)
- [https://github.com/square/picasso](https://github.com/square/picasso)
- [https://github.com/google/ExoPlayer](https://github.com/google/ExoPlayer)

## 相关链接
- ios版：[https://gitee.com/hycong/dd-monitor-ios](https://gitee.com/hycong/dd-monitor-ios)
- [https://gitee.com/zhimingshenjun/DD_Monitor_latest](https://gitee.com/zhimingshenjun/DD_Monitor_latest)
- [https://github.com/lovelyyoshino/Bilibili-Live-API](https://github.com/lovelyyoshino/Bilibili-Live-API)
- [https://github.com/MoyuScript/bilibili-api](https://github.com/MoyuScript/bilibili-api)