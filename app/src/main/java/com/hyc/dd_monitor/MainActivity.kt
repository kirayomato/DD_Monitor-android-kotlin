package com.hyc.dd_monitor

import WbiSigner.getWbiKeys
import android.app.Notification
import android.content.*
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.app.Service
import android.os.Build
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.Context
import androidx.core.app.NotificationCompat
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.*
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.zxing.integration.android.IntentIntegrator
import com.hyc.dd_monitor.models.UPInfo
import com.hyc.dd_monitor.views.*

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.regex.Pattern

var headers = Headers.Builder().add(
        "accept", "application/json, text/plain, */*"
                                   ).add("accept-encoding", "utf-8, deflate, zstd")
    .add("accept-language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7").add("cache-control", "no-cache")
    .add("pragma", "no-cache").add("connection", "keep-alive")
    .add("origin", "https://live.bilibili.com").add("referer", "https://live.bilibili.com/27628019")
    .add(
            "sec-ch-ua",
            "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\""
        ).add("sec-ch-ua-mobile", "?0").add("sec-ch-ua-platform", "\"Windows\"")
    .add("sec-fetch-dest", "document").add("sec-fetch-mode", "navigate")
    .add("sec-fetch-site", "none").add("sec-fetch-user", "?1").add(
            "user-agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    .build()

var img_key = ""
var sub_key = ""

lateinit var upinfos: HashMap<String, UPInfo>

class MyForegroundService : Service() {

    private val CHANNEL_ID = "foreground_service_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 创建通知
        val notification: Notification =
                NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("DD播放器")
                    .setContentText("运行中").setSmallIcon(R.mipmap.ic_launcher).build()

        // 启动前台服务
        startForeground(1, notification)

        // 如果服务被系统杀死，返回START_STICKY让服务重新启动
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Foreground Service Channel"
            val descriptionText = "Channel for foreground service notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            // 注册通知渠道
            val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

@UnstableApi
class MainActivity : AppCompatActivity() {

    lateinit var ddLayout: DDLayout

    lateinit var uplist: MutableList<String>

    lateinit var uplistview: ListView
    lateinit var uplistviewAdapter: BaseAdapter

    lateinit var cancelDragView: Button

    lateinit var drawer: DrawerLayout
    lateinit var drawerContent: LinearLayout

    lateinit var toolbar: FrameLayout

    lateinit var volumeBtn: Button

    var isGlobalMuted = false

    var autoSleepMinutes: Int = 0
    var autoSleepTimer: Timer? = null

    lateinit var timerTextView: TextView

//    var isLiveMap: HashMap<String, Boolean> = HashMap()
//    var roomIdToCheck: String? = null

    var lastClipboard: String? = null
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { // 切换到 IO 线程
            val wbi = getWbiKeys()
            img_key = wbi.first
            sub_key = wbi.second
        }

        Log.d("resume", "onResume")

        // 屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        window.decorView.post {
//            for (p in 0 until ddLayout.layoutPlayerCount) {
//                ddLayout.players[p].adjustControlBar()
//            }

            // 获取剪贴板，需要post之后才能获取到
            (getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.let {
                it.primaryClip?.let { clip ->
                    Log.d("clipboard", clip.itemCount.toString())
                    if (clip.itemCount == 0) return@post
                    clip.getItemAt(0)?.let { item ->
                        if (item.text.isNullOrBlank()) return@post
                        val clipboard = item.text.toString()
                        if (clipboard == lastClipboard) {
                            return@post
                        }
                        Log.d("clipboard", clipboard)
                        lastClipboard = clipboard

                        addRoomFromText(clipboard, true)
                        // 删除剪贴板避免重复提醒
                        it.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }


    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("orientation", "onCreate: ")

        // 安卓9/10以上申请存储权限
        ActivityCompat.requestPermissions(
                this, arrayOf(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
                             ), 111
                                         )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 222
                                             )
        }
        val serviceIntent = Intent(this, MyForegroundService::class.java)
        startService(serviceIntent)

        drawer = findViewById(R.id.main_drawer)
        drawerContent = findViewById(R.id.drawer_content)

        toolbar = findViewById(R.id.top_toolbar)

        val dd = findViewById<LinearLayout>(R.id.stack_view)
        ddLayout = DDLayout(this)

        // 拖放成功后 取消拖拽按钮消失
        ddLayout.onCardDropListener = {
            cancelDragView.visibility = View.GONE
        }

        ddLayout.onPlayerClickListener = {
            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                if (toolbar.visibility == View.GONE) {
                    toolbar.visibility = View.VISIBLE
                }
                else {
                    toolbar.visibility = View.GONE
                }
            }
        }

        dd.addView(ddLayout)

        // 读取up列表
        getSharedPreferences("sp", MODE_PRIVATE).getString("uplist", "")?.let {
            uplist = it.split(" ").toMutableList()
            Log.d("uplist", it)
        }
        Log.d("uplist", uplist[0])
        if (uplist.count() == 0 || uplist[0].isEmpty()) {
            uplist = mutableListOf("47377", "8792912", "21652717", "47867")
        }


        upinfos = HashMap()

        uplistview = findViewById(R.id.up_list_view)
        uplistviewAdapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return uplist.count()
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view = p1 ?: View.inflate(this@MainActivity, R.layout.list_item_up, null)

                val cover = view.findViewById<ImageView>(R.id.up_cover_image)
                val face = view.findViewById<ImageView>(R.id.up_face_image)
                val uname = view.findViewById<TextView>(R.id.up_uname_textview)
                val title = view.findViewById<TextView>(R.id.up_title_textview)
                val shadow = view.findViewById<ImageView>(R.id.shadow_imageview) // 用于拖动的头像view
                val isLiveCover = view.findViewById<TextView>(R.id.up_islive_cover)

                val roomId = uplist[p0]

                if (upinfos.containsKey(roomId)) {
                    val upInfo = upinfos[roomId]
                    try {
                        upInfo?.faceImageUrl?.let { url ->
                            Glide.with(this@MainActivity).load(url)
                                .circleCrop() // 替代 RoundImageTransform
                                .into(shadow) // 用于拖动的头像view

                            Glide.with(this@MainActivity).load(url).circleCrop().into(face)
                        }
                    }
                    catch (e: Exception) {
                        face.setImageDrawable(null)
                        shadow.setImageDrawable(null)
                    }

                    if (upInfo?.isLive == true) {
                        try {
                            Glide.with(this@MainActivity).load(upInfo.coverImageUrl).into(cover)
                        }
                        catch (e: Exception) {
                            cover.setImageDrawable(null)
                        }
                    }
                    else {
                        cover.setImageDrawable(null)
                    }
                    if (upInfo?.uname != null) {
                        uname.text = upInfo.uname
                        uname.setBackgroundColor(Color.TRANSPARENT)
                    }
                    else {
                        uname.text = ""
                        uname.setBackgroundColor(Color.BLACK)
                    }

                    if (upInfo?.title != null) {
                        title.text = upInfo.title
                        title.setBackgroundColor(Color.TRANSPARENT)
                    }
                    else {
                        title.text = ""
                        title.setBackgroundColor(Color.BLACK)
                    }

                    if (upInfo?.isLive == true) {
                        isLiveCover.visibility = View.GONE
                    }
                    else {
                        isLiveCover.visibility = View.VISIBLE
                        isLiveCover.text = "未开播"
                    }
                }

                return view
            }
        }

        uplistview.adapter = uplistviewAdapter

        // 取消拖拽按钮
        cancelDragView = findViewById(R.id.cancel_drag_view)
        cancelDragView.setOnDragListener { _, dragEvent ->
            if (dragEvent.action == DragEvent.ACTION_DROP) {
                cancelDragView.visibility = View.GONE
            }
            // 拖拽进入时高亮效果
            if (dragEvent.action == DragEvent.ACTION_DRAG_ENTERED) {
                cancelDragView.setBackgroundColor(resources.getColor(R.color.teal_200, theme))
            }
            if (dragEvent.action == DragEvent.ACTION_DRAG_EXITED) {
                cancelDragView.setBackgroundColor(resources.getColor(R.color.teal_700, theme))
            }
            return@setOnDragListener true
        }
        // 折中方案 点击隐藏按钮
        cancelDragView.setOnClickListener {
            cancelDragView.visibility = View.GONE
        }

        // 长按卡片拖拽
        uplistview.setOnItemLongClickListener { adapterView, view, i, l ->
            Log.d("long click", i.toString())

            val clipData = ClipData(
                    "roomId", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), ClipData.Item(uplist[i])
                                   )
            clipData.addItem(ClipData.Item(upinfos[uplist[i]]?.faceImageUrl))

            view.startDragAndDrop(
                    clipData,
                    View.DragShadowBuilder(view.findViewById(R.id.shadow_view)),
                    null,
                    View.DRAG_FLAG_GLOBAL
                                 )

            drawer.closeDrawers()
            cancelDragView.visibility = View.VISIBLE
            cancelDragView.setBackgroundColor(resources.getColor(R.color.teal_700, theme))
            return@setOnItemLongClickListener true
        }

        // 单击卡片弹出菜单
        uplistview.setOnItemClickListener { adapterView, view, i, l ->
            val pop = PopupMenu(this, view)
            pop.menuInflater.inflate(R.menu.up_item_card, pop.menu)
            pop.setOnMenuItemClickListener {
                if (it.itemId == R.id.delete_item) {
                    Log.d("menu", "delete")
                    uplist.removeAt(i)
//                    uplistview.invalidateViews()
                    uplistviewAdapter.notifyDataSetInvalidated()
                    getSharedPreferences("sp", MODE_PRIVATE).edit {
                        this.putString("uplist", uplist.joinToString(" ")).apply()
                    }
                }
                if (it.itemId == R.id.open_live) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("bilibili://live/${uplist[i]}")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                    catch (e: Exception) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("https://live.bilibili.com/${uplist[i]}")
                        startActivity(intent)
                        Log.d("Exception", "Failed: $e")
                    }
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()

        }

        val typeface = Typeface.createFromAsset(assets, "iconfont.ttf")

        // 全局刷新按钮
        val refreshBtn = findViewById<Button>(R.id.refresh_btn)
        refreshBtn.typeface = typeface
        refreshBtn.setOnClickListener {
//            ddLayout.reloadLayout()
            ddLayout.refreshAll()
        }

        // 全局静音按钮
        volumeBtn = findViewById<Button>(R.id.volume_btn)
        volumeBtn.typeface = typeface
        volumeBtn.setOnClickListener {
//            isGlobalMuted = !isGlobalMuted
//            // 修改全部的音量
//            for (p in ddLayout.players) {
//                p.isGlobalMuted = isGlobalMuted
//            }
//            volumeBtn.text = if (isGlobalMuted) "\ue607" else "\ue606"
            val dialog = GlobalVolumeDialog(this, ddLayout)
            dialog.show()
        }

        // 全局弹幕按钮
        val danmuBtn = findViewById<Button>(R.id.danmu_btn)
        danmuBtn.typeface = typeface
        danmuBtn.setOnClickListener {
            val dialog = DanmuOptionsDialog(this, null)
            dialog.onDanmuOptionsChangeListener = {
                // 修改全部的弹幕设置
                for (p in ddLayout.players) {
                    it.volume = p.playerOptions.volume
                    p.playerOptions = it
                    p.notifyPlayerOptionsChange()
                }
            }
            dialog.show()
        }

        // 全局画质按钮
        val qnBtn = findViewById<Button>(R.id.qn_btn)
        qnBtn.setOnClickListener {
            val pop = PopupMenu(this, qnBtn)
            pop.menuInflater.inflate(R.menu.qn_menu, pop.menu)
            pop.setOnMenuItemClickListener {
                var newQn = 150
                when (it.itemId) {
                    R.id.qn_10000 -> newQn = 10000
                    R.id.qn_400 -> newQn = 400
                    R.id.qn_250 -> newQn = 250
                    R.id.qn_150 -> newQn = 150
                }
                // 修改全部的画质 setter自动刷新
                for (p in ddLayout.players) {
                    p.qn = newQn
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        // 关于按钮
        val aboutBtn = findViewById<Button>(R.id.about_btn)
        aboutBtn.typeface = typeface
        aboutBtn.setOnClickListener {
//            throw Exception("oops")
            val ver = packageManager.getPackageInfo(packageName, 0).versionName
            AlertDialog.Builder(this).setTitle("DD监控室 v${ver} by CongHu")
                .setMessage("· 点击右上角“UP”按钮添加UP主，长按拖动到播放器窗口内。\n· 观看多个直播时请注意带宽网速、流量消耗、电池电量、机身发热、系统卡顿等软硬件环境问题。\n· 本软件仅读取公开API数据，不涉及账号登录，欢迎查看源码进行监督。因此，本软件不支持弹幕互动、直播打赏等功能，若要使用请前往原版B站APP。")
                .setPositiveButton("B站视频") { _, _ ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("bilibili://video/BV13y4y1b7bY")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }
                    catch (e: Exception) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("https://www.bilibili.com/video/BV13y4y1b7bY")
                        startActivity(intent)
                        Log.d("Exception", "Failed: $e")
                    }
                }.setNegativeButton("关闭", null).show()
        }

        timerTextView = findViewById(R.id.timer_textview)

        // 定时按钮
        val timerBtn = findViewById<Button>(R.id.timer_btn)
        timerBtn.typeface = typeface
        timerBtn.setOnClickListener {
            val pop = PopupMenu(this, timerBtn)
            pop.menuInflater.inflate(R.menu.timer_menu, pop.menu)
            pop.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.timer_set_15 -> autoSleepTimerSet(15)
                    R.id.timer_set_30 -> autoSleepTimerSet(30)
                    R.id.timer_set_60 -> autoSleepTimerSet(60)
                    R.id.timer_cancel -> autoSleepTimerSet(0)
                    R.id.timer_custom -> {
                        val et = EditText(this)
                        et.inputType = InputType.TYPE_CLASS_NUMBER
                        AlertDialog.Builder(this).setTitle("定时关闭（分钟）").setView(et)
                            .setPositiveButton("确定") { _, _ ->
                                et.text.toString().toIntOrNull()?.let { min ->
                                    if (min in 1..99) autoSleepTimerSet(min)
                                }
                            }.setNegativeButton("取消", null).show()
                        et.requestFocus()
                    }
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        // 横屏按钮
        val landScapeBtn = findViewById<Button>(R.id.landscape_btn)
        landScapeBtn.typeface = typeface
        landScapeBtn.setOnClickListener {
            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                window.insetsController?.apply {
                    show(WindowInsets.Type.systemBars())
                }
                toolbar.visibility = View.VISIBLE
            }
            else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                window.insetsController?.apply {
                    hide(WindowInsets.Type.systemBars())
                }
                toolbar.visibility = View.GONE
            }

        }

        // 切换布局按钮
        val layoutBtn = findViewById<Button>(R.id.layout_btn)
        layoutBtn.typeface = typeface
        layoutBtn.setOnClickListener {
            val dialog = LayoutOptionsDialog(this)
            dialog.onLayoutOptionsSelectedListener = {
                // 判断是否是双击全屏的状态
                if (ddLayout.layoutBeforeFullScreen != null) {
                    val target = ddLayout.players[ddLayout.fullScreenPlayerId!!]
                    ddLayout.players[ddLayout.fullScreenPlayerId!!] = ddLayout.players[0]
                    ddLayout.players[0] = target
                    ddLayout.fullScreenPlayerId = null
                    ddLayout.layoutBeforeFullScreen = null
                }
                ddLayout.layoutId = it
                getSharedPreferences("sp", MODE_PRIVATE).edit {
                    this.putInt("layout", it).apply()
                }

            }
            dialog.show()
        }

        // 打开up列表抽屉
        findViewById<Button>(R.id.uplist_btn).setOnClickListener {
//            throw java.lang.Exception("oopss")
            drawer.openDrawer(drawerContent)
            loadManyUpInfos()
//            for (up in 0 until uplist.count()) {
//                loadUpInfo(uplist[up]) {
//                    if (it == uplist.last()) {
//                        uplist.sortByDescending { id ->
//                            upinfos[id]?.isLive
//                        }
//                        Log.d("sort", "sort")
//                        runOnUiThread {
////                            uplistview.invalidateViews()
//                            uplistviewAdapter.notifyDataSetInvalidated()
//                        }
//                    }
//
//                }
//            }

        }

        // 添加按钮
        findViewById<Button>(R.id.add_up_btn).setOnClickListener {
            val et = EditText(this)
            et.inputType = InputType.TYPE_CLASS_TEXT
            AlertDialog.Builder(this).setTitle("添加直播间号或链接")
                .setMessage("提示：输入完成可按回车键，此窗口不关闭，可继续输入添加。").setView(et)
                .setPositiveButton("确定") { _, _ ->
                    addRoomFromText(et.text.toString())
                }.setNegativeButton("取消", null).show()
//            et.requestFocus()

            // 输入框监听回车键
            et.setOnEditorActionListener { textView, i, keyEvent ->
                Log.d("imeaction", i.toString())
                if (i == EditorInfo.IME_ACTION_DONE) {
                    addRoomFromText(et.text.toString())
                    et.text.clear()
                }
                return@setOnEditorActionListener true
            }
        }

        // 导入按钮
        findViewById<Button>(R.id.uid_import_btn).setOnClickListener {
            val dialog = UidImportDialog(this)
            dialog.onImportFinishedListener = { set ->
                uplist.addAll(set)
                uplistviewAdapter.notifyDataSetInvalidated()
//                for (up in set) {
//                    loadUpInfo(up)
//                }
                loadManyUpInfos()
                getSharedPreferences("sp", MODE_PRIVATE).edit {
                    this.putString("uplist", uplist.joinToString(" ")).apply()
                }
                Toast.makeText(this, "已添加${set.size}项", Toast.LENGTH_SHORT).show()
            }
            dialog.show()
        }

        findViewById<Button>(R.id.scan_qr_btn).setOnClickListener {
            startActivityForResult(Intent(this, ScanQrActivity::class.java), 123)
        }

        // 定时刷新 开播提醒
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                loadManyUpInfos(true)
            }
        }, 30000, 30000)
    }

    @OptIn(UnstableApi::class)
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("orientation", "onConfigurationChanged")
        // 横竖屏切换时触发，调整每个窗口的工具栏以适应当前宽度
        for (p in 0 until ddLayout.layoutPlayerCount) {
            ddLayout.post {
                ddLayout.players[p].adjustControlBar()
            }
        }
    }

    fun addRoomId(id: String) {
//        val roomId = id.toIntOrNull()
        if (!Pattern.compile("\\d+").matcher(id).matches()) {
            runOnUiThread {
                Toast.makeText(this, "无效的id $id", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (uplist.contains(id)) {
            runOnUiThread {
                Toast.makeText(this, "已存在 ${upinfos[id]?.uname ?: id}", Toast.LENGTH_SHORT)
                    .show()
            }
            return
        }
        uplist.add(0, id)
        loadManyUpInfos()
//        loadUpInfo(id) { realRoomId ->
//            if (uplist.contains(realRoomId)) {
//                runOnUiThread {
//                    Toast.makeText(this, "已存在 ${upinfos[id]?.uname ?: id}", Toast.LENGTH_SHORT)
//                        .show()
//                }
//                return@loadUpInfo
//            }

        runOnUiThread {
            getSharedPreferences("sp", MODE_PRIVATE).edit {
                this.putString("uplist", uplist.joinToString(" ")).apply()
            }
            uplistviewAdapter.notifyDataSetInvalidated()

            Toast.makeText(this, "已添加id ${upinfos[id]?.uname ?: id}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 123) {
            try {
                val res = IntentIntegrator.parseActivityResult(resultCode, data).contents
                Log.d("scanqr", res)
                addRoomFromText(res)
            }
            catch (e: Exception) {
                Log.d("Exception", "Failed: $e")
            }

        }
    }

    fun addRoomFromURL(url: URL) {
        if (url.host == "live.bilibili.com") {
            try {
                """live\.bilibili\.com\/(?:[^/]+/)*?(\d+)""".toRegex()
                    .find(url.toString())!!.groupValues[1].let {
                    addRoomId(it)
                }
            }
            catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                            this@MainActivity, "链接解析失败${e}", Toast.LENGTH_SHORT
                                  ).show()
                }
            }
        }
        else if (listOf("b23.tv", "bili2233.cn").contains(url.host)) {
            OkHttpClient().newCall(
                    Request.Builder().url(url.toString()).headers(headers).build()
                                  ).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {

                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        Log.d("Exception", "Request failed with code: ${response.code}")
                        runOnUiThread {
                            Toast.makeText(
                                    this@MainActivity,
                                    "Request failed with code: ${response.code}",
                                    Toast.LENGTH_SHORT
                                          ).show()
                        }
                        return
                    }
                    try {
                        """"room_id":(\d+)""".toRegex()
                            .find(response.body!!.string())!!.groupValues[1].let {
                            addRoomId(it)
                        }
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(
                                    this@MainActivity, "链接解析失败${e}", Toast.LENGTH_SHORT
                                          ).show()
                        }
                    }
                }
            })
        }
        else {
            runOnUiThread {
                Toast.makeText(
                        this@MainActivity, "链接无法识别", Toast.LENGTH_SHORT
                              ).show()
            }
        }
    }

    fun addRoomFromText(clip: String, fromClip: Boolean = false) {
        try {
            if (clip.all { it.isDigit() }) {
                if (fromClip) {
                    AlertDialog.Builder(this).setTitle("尝试添加剪贴板的直播间号？").setMessage(clip)
                        .setPositiveButton("确认") { _, _ -> addRoomId(clip) } // 确认后执行逻辑
                        .setNegativeButton("取消", null).show()
                }
                else {
                    addRoomId(clip)
                }
            }
            else {
                """(https?://\S+)""".toRegex().find(clip)?.groupValues?.get(1)?.let { it ->
                    val url = URL(it)
                    if (fromClip) {
                        AlertDialog.Builder(this).setTitle("尝试添加剪贴板的链接？").setMessage(it)
                            .setPositiveButton("确认") { _, _ -> addRoomFromURL(url) } // 确认后执行逻辑
                            .setNegativeButton("取消", null).show()
                    }
                    else {
                        addRoomFromURL(url)
                    }
                }
            }
        }
        catch (e: Exception) {
            Log.d("Exception", "Failed: $e")
        }
    }

    // 读取单个直播间信息 不可并发、不可频繁请求
    fun loadUpInfo(roomId: String, finished: ((realRoomId: String) -> Unit)? = null) {
        OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=$roomId")
                    .headers(headers).build()
                              ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.d("Exception", "Request failed with code: ${response.code}")
                    runOnUiThread {
                        Toast.makeText(
                                this@MainActivity,
                                "Request failed with code: ${response.code}",
                                Toast.LENGTH_SHORT
                                      ).show()
                    }
                    return
                }
                Log.d("loadinfo", roomId)
                response.body?.let {
                    try {
                        val jo = JSONObject(it.string())
                        val code = jo.getInt("code")
                        require(code == 0) { "Return Code Error:$code" }
                        val data = jo.getJSONObject("data")
                        val roomInfo = data.getJSONObject("room_info")
                        val anchorInfo =
                                data.getJSONObject("anchor_info").getJSONObject("base_info")

                        val realRoomId = roomInfo.getString("room_id")


                        val upInfo = UPInfo()
                        upInfo.title = roomInfo.getString("title")
                        var keyframe = roomInfo.getString("keyframe")
                        if (keyframe.startsWith("http://")) {
                            keyframe = keyframe.replace("http://", "https://")
                        }
                        upInfo.coverImageUrl = keyframe
                        upInfo.isLive = roomInfo.getInt("live_status") == 1
                        upInfo.uname = anchorInfo.getString("uname")
                        var face = anchorInfo.getString("face")
                        if (face.startsWith("http://")) {
                            face = face.replace("http://", "https://")
                        }
                        upInfo.faceImageUrl = face

                        upinfos[realRoomId] = upInfo
                        finished?.invoke(realRoomId)

//                        if (uplist.indexOf(roomId) == uplist.count() - 1) {
//                            uplist.sortByDescending { id ->
//                                if (upinfos.containsKey(id)) upinfos[id]?.isLive else false
//                            }
//                        }


                        runOnUiThread {
                            uplistviewAdapter.notifyDataSetInvalidated()
                        }
                    }
                    catch (e: Exception) {
                        Log.d("Exception", e.toString())
                        runOnUiThread {
                            Toast.makeText(
                                    this@MainActivity, "查询id失败 $roomId", Toast.LENGTH_SHORT
                                          ).show()
                        }
                    }


                }
            }

        })
    }

    // 读取多条直播间信息
    fun loadManyUpInfos(reportLiveStarting: Boolean = false) {
        val postdata = "{\"ids\":[${uplist.joinToString(",")}]}"
        Log.d("loadinfo", postdata)
        val body = postdata.toRequestBody("application/json; charset=utf-8".toMediaType())
        OkHttpClient().newCall(
                Request.Builder().url("https://api.live.bilibili.com/room/v2/Room/get_by_ids")
                    .method("POST", body).headers(headers).build()
                              ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                            this@MainActivity, "查询信息失败 $e", Toast.LENGTH_SHORT
                                  ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.d("Exception", "Request failed with code: ${response.code}")
                    runOnUiThread {
                        Toast.makeText(
                                this@MainActivity,
                                "Request failed with code: ${response.code}",
                                Toast.LENGTH_SHORT
                                      ).show()
                    }
                    return
                }
                response.body?.let {
                    try {
                        val jo = JSONObject(it.string())
                        val code = jo.getInt("code")
                        require(code == 0) { "Return Code Error:$code" }
                        val res = jo.getJSONObject("data")


                        val uids = mutableListOf<String>()
                        for (k in res.keys()) {
                            try {
                                uids.add(res.getJSONObject(k).getString("uid"))
                            }
                            catch (e: Exception) {
                                Log.d("Exception", "Request Failed: $e")
                                runOnUiThread {
                                    Toast.makeText(
                                            this@MainActivity,
                                            "Request Failed: $e",
                                            Toast.LENGTH_SHORT
                                                  ).show()
                                }
                            }
                        }
                        Log.d("loadinfo", uids.joinToString(","))
                        val body1 = Gson().toJson(mapOf(Pair("uids", uids)))
                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                        OkHttpClient().newCall(
                                Request.Builder()
                                    .url("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids")
                                    .method("POST", body1).headers(headers).build()
                                              ).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                Log.d("Exception", "加载信息失败: $e")
                                runOnUiThread {
                                    Toast.makeText(
                                            this@MainActivity,
                                            "加载信息失败: $e",
                                            Toast.LENGTH_SHORT
                                                  ).show()
                                }
                            }

                            @OptIn(UnstableApi::class)
                            override fun onResponse(call: Call, response: Response) {
                                if (!response.isSuccessful) {
                                    Log.d("Exception", "Request failed with code: ${response.code}")
                                    runOnUiThread {
                                        Toast.makeText(
                                                this@MainActivity,
                                                "Request failed with code: ${response.code}",
                                                Toast.LENGTH_SHORT
                                                      ).show()
                                    }
                                    return
                                }
                                response.body?.let { it1 ->
                                    try {
                                        val jo1 = JSONObject(it1.string())
                                        val code1 = jo1.getInt("code")
                                        require(code1 == 0) { "Return Code Error:$code1" }
//                                        Log.d("loadinfo", jo1.toString())
                                        val res1 = jo1.getJSONObject("data")
                                        val reportLiveStartingList = mutableListOf<String>()

                                        for (k in res1.keys()) {

                                            var realRoomId: String? = null
                                            val upInfo = UPInfo()
                                            try {
                                                val data = res1.getJSONObject(k)
                                                realRoomId = data.getString("room_id")

                                                upInfo.uname = data.getString("uname")

                                                upInfo.isLive = data.getInt("live_status") == 1
                                                if (upInfo.isLive && upinfos.containsKey(realRoomId) && upinfos[realRoomId]?.isLive == false) {
                                                    Log.d("kaibo", upInfo.uname ?: "")
                                                    reportLiveStartingList.add(upInfo.uname ?: "??")
                                                }

                                                upInfo.title = data.getString("title")

                                                var face = data.getString("face")
                                                if (face.startsWith("http://")) {
                                                    face = face.replace("http://", "https://")
                                                }
                                                upInfo.faceImageUrl = face

                                                var keyframe = data.getString("keyframe")
                                                if (keyframe.startsWith("http://")) {
                                                    keyframe =
                                                            keyframe.replace("http://", "https://")
                                                }
                                                upInfo.coverImageUrl = keyframe


                                            }
                                            catch (e: Exception) {
                                                Log.d("Exception", "Request Failed: $e")
                                                runOnUiThread {
                                                    Toast.makeText(
                                                            this@MainActivity,
                                                            "Request Failed: $e",
                                                            Toast.LENGTH_SHORT
                                                                  ).show()
                                                }
                                            }

                                            if (realRoomId != null) upinfos[realRoomId] = upInfo
                                        }

                                        uplist.sortByDescending { id ->
                                            upinfos[id]?.isLive
                                        }
                                        runOnUiThread {
                                            uplistviewAdapter.notifyDataSetInvalidated()
                                            if (reportLiveStartingList.count() > 0) {
                                                if (reportLiveStarting) {
                                                    // 开播提醒
//                                                    Toast.makeText(
//                                                            this@MainActivity,
//                                                            "${reportLiveStartingList.joinToString(", ")} 开播了",
//                                                            Toast.LENGTH_LONG
//                                                    ).show()
                                                    Snackbar.make(
                                                            window.decorView,
                                                            "${reportLiveStartingList.joinToString(", ")} 开播了",
                                                            Snackbar.LENGTH_LONG
                                                                 ).setAction("关闭") {}.show()
                                                }
                                                for (i in 0 until ddLayout.layoutPlayerCount) {
                                                    val p = ddLayout.players[i]
                                                    if (p.roomId != null && reportLiveStartingList.contains(
                                                                    p.roomId
                                                                                                           )
                                                    ) {
                                                        p.roomId = p.roomId
                                                    }
                                                }
                                            }

                                        }
                                    }
                                    catch (e: Exception) {
                                        Log.d("Exception", "Request Failed: $e")
                                        runOnUiThread {
                                            Toast.makeText(
                                                    this@MainActivity,
                                                    "查询信息失败 $e",
                                                    Toast.LENGTH_SHORT
                                                          ).show()
                                        }
                                    }
                                }

                            }

                        })


                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(
                                    this@MainActivity, "查询信息失败 $e", Toast.LENGTH_SHORT
                                          ).show()
                        }
                    }
                }
            }

        })
    }

    // 设置定时关闭
    fun autoSleepTimerSet(min: Int) {
        autoSleepMinutes = min
        autoSleepTimer?.cancel()
        autoSleepTimer = null
        if (autoSleepMinutes <= 0) {
            timerTextView.text = ""
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            return
        }
        timerTextView.text = "$autoSleepMinutes"


        autoSleepTimer = Timer()
        autoSleepTimer!!.schedule(object : TimerTask() {
            override fun run() {
                autoSleepMinutes -= 1
                runOnUiThread {
                    timerTextView.text = "$autoSleepMinutes"
                }
                if (autoSleepMinutes == 0) {
                    runOnUiThread {
                        timerTextView.text = ""
                        autoSleepTimer?.cancel()
                        for (p in ddLayout.players) {
                            p.player?.pause()
                        }
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        Toast.makeText(this@MainActivity, "已恢复系统自动锁屏", Toast.LENGTH_SHORT)
                            .show()
                    }

                }
            }

        }, 60000, 60000)
    }

    override fun onDestroy() {
        super.onDestroy()
        for (p in ddLayout.players) {
            p.player?.stop()
        }
    }

    // 再按一次返回键退出
    var backPressTime: Long = 0
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (drawer.isDrawerOpen(GravityCompat.END)) {
                drawer.closeDrawers()
                return true
            }
            else if (System.currentTimeMillis() - backPressTime > 2000) {
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
                backPressTime = System.currentTimeMillis()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }
}