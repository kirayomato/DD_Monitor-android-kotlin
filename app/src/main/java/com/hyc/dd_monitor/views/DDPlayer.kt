package com.hyc.dd_monitor.views

import WbiSigner.encWbi
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.hyc.dd_monitor.R
import com.hyc.dd_monitor.headers
import com.hyc.dd_monitor.models.PlayerOptions
import com.hyc.dd_monitor.utils.RecordingUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.brotli.dec.BrotliInputStream
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.zip.InflaterInputStream
import com.hyc.dd_monitor.upinfos
import com.hyc.dd_monitor.img_key
import com.hyc.dd_monitor.sub_key
import android.content.ClipboardManager
import androidx.media3.common.PlaybackException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@UnstableApi
class DDPlayer(context: Context, playerId: Int) : ConstraintLayout(context) {


    val liveHeaders = headers.newBuilder()
    val myHandler = Handler(Looper.getMainLooper())
    var host = "broadcastlv.chat.bilibili.com"
    var token = ""

    val transferred = ArrayDeque<Long>()
    var totalbytes = 0L
    val transferListener = object : TransferListener {
        override fun onTransferInitializing(
                source: DataSource, dataSpec: DataSpec, isNetwork: Boolean
                                           ) {
        }

        override fun onTransferStart(
                source: DataSource, dataSpec: DataSpec, isNetwork: Boolean
                                    ) {
        }

        override fun onBytesTransferred(
                source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int
                                       ) {
            totalbytes += bytesTransferred.toLong()
        }

        override fun onTransferEnd(
                source: DataSource, dataSpec: DataSpec, isNetwork: Boolean
                                  ) {
        }
    }

    // 设置窗口序号#
    var playerId: Int = playerId
        set(value) {
            playerNameBtn.text = playerNameBtn.text.replace(Regex("#${field + 1}"), "#${value + 1}")
            shadowTextView.text = "#${value + 1}"
            field = value

        }

    var playerOptions = PlayerOptions()

    // 刷新画质
    var qn: Int = 150
        set(value) {
            if (field != value) {
                field = value
                qnBtn.text = "画质"
                when (value) {
                    10000 -> qnBtn.text = "原画"
                    400 -> qnBtn.text = "蓝光"
                    250 -> qnBtn.text = "超清"
                    150 -> qnBtn.text = "高清"
                }
                if (isRecording) {
                    isRecording = false
                }
                else {
                    this.roomId = roomId
                }
                playerOptions.qn = value
                notifyPlayerOptionsChange()
            }
        }

    var playerNameBtn: Button
    var playerView: PlayerView
    var controlBar: LinearLayout

    var danmuView: LinearLayout
    var danmuListView: ListView
    var interpreterListView: ListView
    var danmuListViewAdapter: BaseAdapter
    var interpreterViewAdapter: BaseAdapter

    var danmuList: MutableList<Pair<String, String?>> = mutableListOf()
    var interpreterList: MutableList<String> = mutableListOf()

    var onDragAndDropListener: ((drag: Int, drop: Int) -> Unit)? = null
    var onCardDropListener: (() -> Unit)? = null

    var onPlayerClickListener: (() -> Unit)? = null

//    var volumeBar: LinearLayout
//    var volumeSlider: SeekBar
//    var volumeAdjusting = false

    var refreshBtn: Button
    var volumeBtn: Button
    var danmuBtn: Button

    // 设置是否全局静音
    var isGlobalMuted = false
        set(value) {
            field = value
            player?.volume = if (value) 0f else playerOptions.volume
        }

    var qnBtn: Button

    var isHiddenBarBtns = false

    var shadowView: View
    var shadowFaceImg: ImageView
    var shadowTextView: TextView
    var speedTextView: TextView

    var hideControlTimer: Timer? = null

    var doubleClickTime: Long = 0
    var onDoubleClickListener: ((playerId: Int) -> Unit)? = null

    var recordingTimer: Timer? = null
    var isRecording = false
    var recordingView: LinearLayout

    //    var recordingDuration: TextView
    var recordingSize: TextView

    var volumeChangedListener: SeekBar.OnSeekBarChangeListener

    init {
        liveHeaders["connection"] = "Upgrade"

        layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                                             )

        View.inflate(context, R.layout.dd_player, this)
        Log.d("DDPlayer", "init")

        playerNameBtn = findViewById(R.id.player_name_btn)
        playerView = findViewById(R.id.dd_player_view)
        controlBar = findViewById(R.id.player_control_bar)
        danmuView = findViewById(R.id.danmu_view)
        danmuListView = findViewById(R.id.danmu_listView)
        interpreterListView = findViewById(R.id.interpreter_listView)
//        volumeBar = findViewById(R.id.volume_bar)
//        volumeSlider = findViewById(R.id.volume_slider)
        qnBtn = findViewById(R.id.qn_btn)

        recordingView = findViewById(R.id.recording_view)
//        recordingDuration = findViewById(R.id.recording_duration_textview)
        recordingSize = findViewById(R.id.recording_size_textview)

        recordingView.setOnClickListener {
            val pop = PopupMenu(context, recordingView)
            pop.menu.add(0, 999, 0, "结束录像")
            pop.setOnMenuItemClickListener {
                if (it.itemId == 999) {
                    isRecording = false
//                    roomId = roomId
                }
                return@setOnMenuItemClickListener true
            }
        }

//        danmuTextView.movementMethod = ScrollingMovementMethod.getInstance()
        danmuListViewAdapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return danmuList.count()
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view = p1 ?: View.inflate(context, R.layout.item_danmu_text, null)
                val textview = view.findViewById<TextView>(R.id.danmu_textView)
                val imgview = view.findViewById<ImageView>(R.id.danmu_imgview)
                val danmuObj = danmuList[p0]
                if (danmuObj.second != null) {
                    textview.visibility = GONE
                    imgview.visibility = VISIBLE
                    Glide.with(context).load(danmuObj.second).into(imgview)
                }
                else {
                    textview.visibility = VISIBLE
                    imgview.visibility = GONE
                }
                textview.text = danmuObj.first

                textview.textSize = playerOptions.danmuSize.toFloat()
                return view
            }

        }
        danmuListView.adapter = danmuListViewAdapter
        danmuListView.setOnItemClickListener { adapterView, view, i, l ->
            val danmu = danmuList[i]
            val pop = PopupMenu(context, view)
            pop.menuInflater.inflate(R.menu.danmu_clear, pop.menu)
            pop.setOnMenuItemClickListener { it ->
                if (it.itemId == R.id.danmu_clear) {
                    danmuList.remove(danmu)
                    danmuListViewAdapter.notifyDataSetInvalidated()
                }
                else if (it.itemId == R.id.danmu_clear_all) {
                    danmuList.removeAll(danmuList)
                    danmuListViewAdapter.notifyDataSetInvalidated()
                }
                else if (it.itemId == R.id.danmu_copy) {
                    context.getSystemService(Context.CLIPBOARD_SERVICE)?.apply {
                        if (this is ClipboardManager) {
                            setPrimaryClip(ClipData.newPlainText("label", danmu.first))
                        }
                    }
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        interpreterViewAdapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return interpreterList.count()
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view = p1 ?: View.inflate(context, R.layout.item_danmu_text, null)
                val textview = view.findViewById<TextView>(R.id.danmu_textView)
                textview.text = interpreterList[p0]

                textview.textSize = playerOptions.danmuSize.toFloat()
                return view
            }

        }

        // 音量调节
        volumeChangedListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                player?.volume = if (isGlobalMuted) 0f else p1.toFloat() / 100f
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
//                volumeAdjusting = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
//                volumeAdjusting = false
//                showControlBar()
//                if (p0 != null && p0 != volumeSlider) {
//                    volumeSlider.progress = p0.progress
//                }
                playerOptions.volume = p0!!.progress.toFloat() / 100f
                notifyPlayerOptionsChange()
            }
        }

        interpreterListView.adapter = interpreterViewAdapter
        interpreterListView.setOnItemClickListener { adapterView, view, i, l ->
            val danmu = interpreterList[i]
            val pop = PopupMenu(context, view)
            pop.menuInflater.inflate(R.menu.danmu_clear, pop.menu)
            pop.setOnMenuItemClickListener {
                if (it.itemId == R.id.danmu_clear) {
                    interpreterList.remove(danmu)
                    interpreterViewAdapter.notifyDataSetInvalidated()
                }
                else if (it.itemId == R.id.danmu_clear_all) {
                    interpreterList.removeAll(interpreterList)
                    interpreterViewAdapter.notifyDataSetInvalidated()
                }
                else if (it.itemId == R.id.danmu_copy) {
                    context.getSystemService(Context.CLIPBOARD_SERVICE)?.apply {
                        if (this is ClipboardManager) {
                            setPrimaryClip(ClipData.newPlainText("label", danmu))
                        }
                    }
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        shadowView = findViewById(R.id.shadow_view)
        shadowFaceImg = findViewById(R.id.shadow_imageview)
        shadowTextView = findViewById(R.id.shadow_textview)
        speedTextView = findViewById(R.id.speed_textview)

        playerNameBtn.text = "#${playerId + 1}: 空"
        shadowTextView.text = "#${playerId + 1}"

        // 点击窗口名称弹出菜单
        playerNameBtn.setOnClickListener {
            if (roomId == null) return@setOnClickListener
            val pop = PopupMenu(context, playerNameBtn)
            val menuId = if (isHiddenBarBtns) R.menu.player_options_more else R.menu.player_options
            pop.menuInflater.inflate(menuId, pop.menu)
//            if (player != null ) { // && player?.isPlaying == true
//                if (recordingTimer == null) {
//                    pop.menu.add(0, 666, 0, "开始录制(beta)")
//                }else{
//                    pop.menu.add(0, 999, 0, "结束录制")
//                }
//            }

            pop.setOnMenuItemClickListener {
                if (it.itemId == R.id.window_close) {
                    roomId = null
                    context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
                        this.putString("roomId${this@DDPlayer.playerId}", null).apply()
                    }
                }
                if (it.itemId == R.id.open_live) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("bilibili://live/$roomId")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)

                        playerOptions.volume = 0f
                        notifyPlayerOptionsChange()
                    }
                    catch (_: Exception) {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse("https://live.bilibili.com/$roomId")
                        context.startActivity(intent)
                    }

                }
                // 开始录制
                if (it.itemId == 666) {
                    isRecording = true
                    roomId = roomId
                }
                // 结束录像
                if (it.itemId == 999) {
                    isRecording = false
//                    roomId = roomId
                }
                // 下面是工具栏宽度不足时，要呈现的菜单项
                if (it.itemId == R.id.refresh_btn) {
                    this.roomId = roomId
                }
                if (it.itemId == R.id.volume_btn) {
                    // 统一使用弹出式音量调节
                    val dialog = VolumeControlDialog(context)
                    dialog.title = "音量调节: ${playerNameBtn.text}"
                    dialog.onSeekBarListener = volumeChangedListener
                    dialog.volume = (playerOptions.volume * 100f).toInt()
                    dialog.show()
//                    if (height < context.resources.displayMetrics.density * 130) {
//                        val dialog = VolumeControlDialog(context)
//                        dialog.title = "音量调节: ${playerNameBtn.text}"
//                        dialog.onSeekBarListener = volumeChangedListener
//                        dialog.volume = volumeSlider.progress
//                        dialog.show()
//                    }else{
//                        volumeBar.visibility = VISIBLE
//                    }
                }
                if (it.itemId == R.id.danmu_btn) {
                    showDanmuDialog()
                }
                if (it.itemId == R.id.qn_btn) {
                    showQnMenu()
                }
                return@setOnMenuItemClickListener true
            }
            pop.show()
        }

        // 长按拖动功能
        setOnLongClickListener {
            showControlBar()

            startDragAndDrop(
                    ClipData(
                            "layoutId",
                            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                            ClipData.Item(this@DDPlayer.playerId.toString())
                            ), DragShadowBuilder(shadowView), null, View.DRAG_FLAG_GLOBAL
                            )
            return@setOnLongClickListener true
        }

        // 接收拖放
        setOnDragListener { view, dragEvent ->
            if (dragEvent.action == DragEvent.ACTION_DROP) {
                Log.d("drop", dragEvent.clipData.description.label.toString())
                val label = dragEvent.clipData.description.label.toString()

                if (label == "layoutId") { // 从其他窗口拖放进来
                    val dragPid = dragEvent.clipData.getItemAt(0).text.toString().toInt()
                    Log.d("drop", "isSelf? $dragPid ${this.playerId}")
                    if (dragPid != this.playerId) { // 判断不是自己拖放给自己
                        Log.d("drop", "change $dragPid")
                        // 与其他窗口交换，交给上级ddlayout处理
                        onDragAndDropListener?.invoke(dragPid, this.playerId)
//                        this.playerId = dragPid
//                        showControlBar()
                    }
                }
                else if (label == "roomId") { // 从列表的卡片拖动进来
                    // roomId setter 开始播放
                    roomId = dragEvent.clipData.getItemAt(0).text.toString()
                    val face = dragEvent.clipData.getItemAt(1).text.toString()
                    try {
                        Glide.with(context).load(face).circleCrop()
                            .into(shadowFaceImg) // 用于拖动的头像view
                    }
                    catch (e: Exception) {
                        Log.d("Exception", "Failed: $e")
                    }
//                    Log.d("shadowFaceImg", shadowFaceImg)
                    onCardDropListener?.invoke()
                    context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
                        this.putString("roomId${this@DDPlayer.playerId}", roomId).apply()
                    }
                }
//                Log.d("drop $playerId", dragEvent.clipData.getItemAt(0).text.toString())
            }
            // 拖动进入的时候显示一下东西
            if (dragEvent.action == DragEvent.ACTION_DRAG_ENTERED) {
                showControlBar()
            }
            return@setOnDragListener true
        }

        setOnClickListener {
            // 单击显示/隐藏工具条
            if (controlBar.visibility == VISIBLE) {
                controlBar.visibility = INVISIBLE
            }
            else {
                showControlBar()
            }
//            volumeBar.visibility = INVISIBLE

            // 双击全屏
            if (System.currentTimeMillis() - doubleClickTime < 300) {
                doubleClickTime = 0
                Log.d("doubleclick", "doubleclick")
                onDoubleClickListener?.invoke(this.playerId) // 全屏需要刷新layout
            }
            else {
                doubleClickTime = System.currentTimeMillis()
            }

            onPlayerClickListener?.invoke()
        }

        val typeface = Typeface.createFromAsset(context.assets, "iconfont.ttf")

        // 刷新按钮
        refreshBtn = findViewById(R.id.refresh_btn)
        refreshBtn.typeface = typeface
        refreshBtn.setOnClickListener {
            if (isRecording) {
                isRecording = false
            }
            else {
                this.roomId = roomId
            }
        }

        // 音量按钮
        volumeBtn = findViewById(R.id.volume_btn)
        volumeBtn.typeface = typeface
        volumeBtn.setOnClickListener {
//            showControlBar()

            // 统一使用弹出式音量调节
            val dialog = VolumeControlDialog(context)
            dialog.title = "音量调节: ${playerNameBtn.text}"
            dialog.onSeekBarListener = volumeChangedListener
            dialog.volume = (playerOptions.volume * 100f).toInt()
            dialog.show()

//            if (height < context.resources.displayMetrics.density * 130) {
//                val dialog = VolumeControlDialog(context)
//                dialog.title = "音量调节: ${playerNameBtn.text}"
//                dialog.onSeekBarListener = volumeChangedListener
//                dialog.volume = volumeSlider.progress
//                dialog.show()
//            }else{
//                if (volumeBar.visibility == VISIBLE) {
//                    volumeBar.visibility = INVISIBLE
//                }else{
//                    volumeBar.visibility = VISIBLE
//                }
//            }


        }
        // 弹幕按钮
        danmuBtn = findViewById(R.id.danmu_btn)
        danmuBtn.typeface = typeface
        danmuBtn.setOnClickListener {
            showDanmuDialog()
        }

//        volumeSlider.addOnChangeListener { slider, value, fromUser ->
//            player?.volume = if (isGlobalMuted) 0f else value/100f
//        }
//        volumeSlider.setOnSeekBarChangeListener(volumeChangedListener)

        // 静音按钮
//        val muteBtn = findViewById<Button>(R.id.mute_btn)
//        muteBtn.typeface = typeface
//        muteBtn.setOnClickListener {
//            if (playerOptions.volume == 0f) {
//                player?.volume = if (isGlobalMuted) 0f else .5f
//                playerOptions.volume = .5f
//            }else{
//                player?.volume = 0f
//                playerOptions.volume = 0f
//            }
//            if (volumeSlider.progress == 0) {
//                volumeSlider.progress = 50
//                player?.volume = if (isGlobalMuted) 0f else .5f
//                playerOptions.volume = .5f
//            }else{
//                volumeSlider.progress = 0
//                player?.volume = 0f
//                playerOptions.volume = 0f
//            }
//            notifyPlayerOptionsChange()
//
//        }

        // 画质按钮
        qnBtn.setOnClickListener {
            showQnMenu()
        }

        // 读取播放器设置
        context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE)
            .getString("opts${this.playerId}", "")?.let {
                try {
                    Log.d("playeroptions", "load $it")
                    playerOptions = Gson().fromJson(it, PlayerOptions::class.java)
                    qn = playerOptions.qn
                    notifyPlayerOptionsChange()
                }
                catch (e: java.lang.Exception) {
                    Log.d("Exception", "Failed: $e")
                }
            }

        showControlBar()
    }

    fun showQnMenu() {
        val pop = PopupMenu(context, qnBtn)
        pop.menuInflater.inflate(R.menu.qn_menu, pop.menu)
        pop.setOnMenuItemClickListener {
            var newQn = 150
            when (it.itemId) {
                R.id.qn_10000 -> newQn = 10000
                R.id.qn_400 -> newQn = 400
                R.id.qn_250 -> newQn = 250
                R.id.qn_150 -> newQn = 150
            }
            if (newQn != qn) {
                qn = newQn
            }
            return@setOnMenuItemClickListener true
        }
        pop.show()
    }

    fun showDanmuDialog() {
        val dialog = DanmuOptionsDialog(context, this.playerId)
        dialog.onDanmuOptionsChangeListener = {
            playerOptions = it
            notifyPlayerOptionsChange()
        }
        dialog.show()
    }

    var player: ExoPlayer? = null

    var socket: WebSocket? = null
    var socketTimer: Timer? = null

    var startTime = ""
    var recordingDurationLong = 0L

    fun addMsg(msg: String, emoji: String? = null) {
        // 合并重复弹幕
        if (danmuList.lastOrNull()?.first == msg) return

        danmuList.add(Pair(msg, emoji))
        danmuListViewAdapter.notifyDataSetInvalidated()
        danmuListView.setSelection(danmuListView.bottom)
        interpreterList.add(msg)
        interpreterViewAdapter.notifyDataSetInvalidated()
        interpreterListView.setSelection(interpreterListView.bottom)
    }


    /**
     * roomId setter 设置后立即开始加载播放
     */
    var roomId: String? = null
        set(value) {
            playerNameBtn.text = "#${playerId + 1}: 空"
            shadowTextView.text = "#${playerId + 1}"

            if (field != value) {
                // 新的id则弹幕清屏
                danmuList.removeAll(danmuList)
                danmuListViewAdapter.notifyDataSetInvalidated()

                interpreterList.removeAll(interpreterList)
                interpreterViewAdapter.notifyDataSetInvalidated()

                isRecording = false
                if (player?.isPlaying == null || player?.isPlaying == false) {
                    playerOptions.volume = 1f
                }
                // 新的id则重置设置
                playerOptions = PlayerOptions()
                notifyPlayerOptionsChange()
            }
            field = value
            // 初始化播放器相关、弹幕socket相关的对象
            initPlayer()

            recordingView.visibility = GONE
            recordingTimer?.cancel()
            recordingTimer = null

            // set null 表示关闭了当前的播放 窗口置空 录像停止
            if (value == null) {
                isRecording = false
                return
            }
            try {
                val upInfo = upinfos[roomId]
                val liveStatus = if (upInfo!!.isLive) "" else "(未开播)"
                myHandler.post {
                    playerNameBtn.text = "#${playerId + 1}: ${liveStatus}${upInfo.uname}"
                    shadowTextView.text = "#${playerId + 1}"
                }
                if (!upInfo.isLive) return
            }
            catch (e: Exception) {
                field = null
                return
            }
            // 到这了就表示不为空了，开始加载
            playerNameBtn.text = "#${playerId + 1}: 加载中"

            liveHeaders["referer"] = "https://live.bilibili.com/${value}"

            checkAndToastCellular()

            // 加载基础信息
//            getBasicinfo()
            connectVideo()
            // 连接弹幕socket
            getDanmuInfo()
        }

    fun initPlayer() {
        playerView.player = null
        player?.stop()
        player?.release()
        socketTimer?.cancel()
        socket?.close(1000, null)

        shadowFaceImg.setImageDrawable(null)

        player = null
        socket = null
        socketTimer = null

        myHandler.removeCallbacksAndMessages(null)
        totalbytes = 0L
        transferred.clear()
        speedTextView.text = "0 KB/s"
    }

    private fun getBasicinfo() {
        OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom?room_id=${roomId}")
                    .headers(headers).build()
                              ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("Exception", "Request failed: $e")
                myHandler.post {
                    addMsg("[系统]获取用户信息失败，${e}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.d("Exception", "Request failed with code: ${response.code}")
                    myHandler.post {
                        addMsg("[系统]获取用户信息失败，Request failed with code: ${response.code}")
                    }
                    return
                }
                response.body?.let {
                    try {
                        val jo = JSONObject(it.string())
                        val code = jo.getInt("code")
                        require(code == 0) { "Return Code Error:$code" }
                        val data = jo.getJSONObject("data")
                        val roomInfo = data.getJSONObject("room_info")
                        val anchorInfo =
                                data.getJSONObject("anchor_info").getJSONObject("base_info")

                        val liveStatus = if (roomInfo.getInt("live_status") == 1) "" else "(未开播)"
                        val uname = anchorInfo.getString("uname")
                        val face = anchorInfo.getString("face").replace("http://", "https://")
//                            Log.d("shadowFaceImg", shadowFaceImg)
                        myHandler.post {
                            playerNameBtn.text = "#${playerId + 1}: $liveStatus$uname"
                            shadowTextView.text = "#${playerId + 1}"
                            try {
                                Glide.with(context).load(face).circleCrop()
                                    .into(shadowFaceImg) // 用于拖动的头像view

                            }
                            catch (e: Exception) {
                                shadowFaceImg.setImageDrawable(null)
                            }
                        }
                        if (liveStatus.isEmpty()) {
                            // 加载视频
                            connectVideo()
                            // 连接弹幕socket
                            getDanmuInfo()
                        }
                        else {

                        }
                    }
                    catch (e: Exception) {
                        Log.d("Exception", "Request failed: $e")
                        myHandler.post {
                            addMsg("[系统]获取用户信息失败，${e}")
                        }
                    }
                }
            }
        })
    }

    private fun getDanmuInfo() {
        val params = mapOf("id" to roomId!!, "type" to "")
        val signedParams = encWbi(params, img_key, sub_key)
        val url =
                "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo".toHttpUrlOrNull()!!
                    .newBuilder().apply {
                        signedParams.forEach { (key, value) ->
                            addQueryParameter(key, value.toString())
                        }
                    }.build()
        OkHttpClient().newCall(
                Request.Builder().url(url).headers(liveHeaders.build()).build()
                              ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("Exception", "Request failed: $e")
                myHandler.post {
                    addMsg("[系统]连接弹幕失败，${e}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.d("Exception", "Request failed with code: ${response.code}")
                    myHandler.post {
                        addMsg("[系统]连接弹幕失败，Request failed with code: ${response.code}")
                    }
                    return
                }
                response.body?.let {
                    try {
                        val bodyString = it.string()
                        Log.d("loadinfo", bodyString)
                        val jo = JSONObject(bodyString)
                        val code = jo.getInt("code")
                        require(code == 0) { "Return Code Error:$code" }
                        val data = jo.getJSONObject("data")
                        token = data.getString("token")
                        host = data.getJSONArray("host_list").getJSONObject(0).getString("host")
                        Log.d("Updated Token", token)
                        Log.d("Updated Host", host)
                        connectDanmu()
                    }
                    catch (e: Exception) {
                        Log.d("Exception", "Parsing error: $e")
                        myHandler.post {
                            addMsg("[系统]连接弹幕失败，${e}")
                        }
                    }
                } ?: run {
                    Log.d("Exception", "Response body is null")
                }
            }
        })
    }

    fun connectVideo() {
        // 连接视频流
        startTime = SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(Date())

        recordingDurationLong = 0L

        // 加载视频流信息
        OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$roomId&qn=$qn&platform=web")
                    .headers(headers).build()
                              ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("Exception", "Request failed: $e")
                myHandler.post {
                    addMsg("[系统] 获取播放链接失败，${e}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.d("Exception", "Request failed with code: ${response.code}")
                    myHandler.post {
                        addMsg("[系统] 获取播放链接失败，Request failed with code: ${response.code}")
                    }
                    return
                }
                response.body?.let { it2 ->
                    var url = ""
                    try {
                        val urlList =
                                JSONObject(it2.string()).getJSONObject("data").getJSONArray("durl")
                        for (i in 0 until urlList.length()) {
                            val tURL = urlList.getJSONObject(i).getString("url")
                            if ("gotcha" in tURL.substringBefore('?') || i == urlList.length() - 1) {
                                url = tURL
                                break
                            }
                        }

                    }
                    catch (e: Exception) {
                        Log.d("Exception", "Request failed: $e")
                        myHandler.post {
                            addMsg("[系统] 获取播放链接失败，${e}")
                        }
                    }
                    if (url.isEmpty()) return

                    Log.d("proxyurl", url)
                    var ql = ""
                    var format = ""
                    try {
                        """(live_[^/?]+)""".toRegex().find(url)?.groupValues?.get(1)?.let {
                            val file = it.split(".")
                            format = file[1]
                            if (format == "m3u8") format = "hls"
                            val parts = file[0].split('_')
                            if (parts.size == 4) {
                                when (parts[3]) {
                                    "4000" -> ql = "蓝光"
                                    "2500" -> ql = "超清"
                                    "1500" -> ql = "高清"
                                }
                            }
                            else ql = "原画"
                            qnBtn.text = ql
                        }
                    }
                    catch (e: Exception) {
                        Log.d("Exception", "URL parsing error: $e")
                    }
                    val dataSourceFactory =
                            DefaultDataSource.Factory(context).setTransferListener(transferListener)
                    val mediaSourceFactory =
                            DefaultMediaSourceFactory(context).setDataSourceFactory(
                                    dataSourceFactory
                                                                                   )


                    myHandler.post {
                        addMsg("[系统] 成功获得播放链接:${url.substringBefore('?')}，当前画质：${ql}，格式：${format}")
                        player =
                                ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory)
                                    .build()
//                            player!!.addListener(object : Player.EventListener{
//                                override fun onEvents(player: Player, events: Player.Events) {
//                                    super.onEvents(player, events)
//                                    for (i in 0 until events.size()) {
//                                        Log.d("exoplayer-event", events[i].toString())
//                                    }
//                                }
//                                override fun onPlayerError(error: ExoPlaybackException) {
//                                    super.onPlayerError(error)
//                                    error.printStackTrace()
//                                }
//                            })

                        playerView.player = player
                        player!!.volume = if (isGlobalMuted) 0f else playerOptions.volume
                        player!!.playWhenReady = true
                        player!!.prepare()
                    }

                    myHandler.postDelayed(object : Runnable {
                        override fun run() {
                            if (transferred.size >= 5) {
                                transferred.removeFirst() // 移除最早加入的数字
                            }
                            transferred.addLast(totalbytes) // 添加新数字
                            var bytesDiff = (transferred.last - transferred.first) / 1024.0
                            if (transferred.size > 1) {
                                bytesDiff /= transferred.size - 1
                            }
                            speedTextView.text = String.format("%.2f KB/s", bytesDiff)
                            myHandler.postDelayed(this, 1000) // 每秒更新一次
                        }
                    }, 1000)

                    if (!isRecording) {
                        // 刷新播放器的函数

                        myHandler.post {
                            player!!.setMediaItem(MediaItem.fromUri(url))

                            player!!.addListener(object : Player.Listener {
                                override fun onPlayerError(error: PlaybackException) {
                                    super.onPlayerError(error)
                                    // 在出现错误时自动刷新播放器
                                    refreshPlayer("ERROR:${error}")
                                }
                            })

//                                override fun onPlaybackStateChanged(state: Int) {
//                                    super.onPlaybackStateChanged(state)
//                                    if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) {
//                                        // 播放结束或空闲时，尝试重新加载媒体
//                                        val msg =
//                                                if (state == Player.STATE_ENDED) "STATE_ENDED" else "STATE_IDLE"
//                                        refreshPlayer(msg)
//                                    }
//                                }
//                            })


//                                val factory = DefaultHttpDataSource.Factory()
//                                factory.setDefaultRequestProperties(mapOf(
//                                    "User-Agent" to WebView(context).settings.userAgentString,
//                                    "Referer" to "https://live.bilibili.com/"
//                                ))
//                                val mediaSource = ProgressiveMediaSource.Factory(factory)
//                                    .createMediaSource(MediaItem.fromUri(url))
//                                player!!.setMediaSource(mediaSource)
                        }
                    }
                    else {
                        Log.d("debug54", "record")
                        var total: Long = 0

                        myHandler.post {
                            player?.addListener(object : Player.Listener {
                                override fun onIsPlayingChanged(isPlaying: Boolean) {
                                    super.onIsPlayingChanged(isPlaying)
                                    Log.d("isplaying", isPlaying.toString())
                                    if (isPlaying) {
                                        recordingView.visibility = VISIBLE
//                                            recordingDuration.text = "0:00"
                                        recordingSize.text = RecordingUtils.byteString(total)
                                        recordingTimer = Timer()
                                        recordingTimer!!.schedule(object : TimerTask() {
                                            override fun run() {
                                                myHandler.post {
                                                    recordingDurationLong += 1
//                                                        recordingDuration.text = ByteUtils.minuteString(recordingDurationLong)
                                                    recordingSize.text =
                                                            RecordingUtils.byteString(total)
                                                }

                                            }
                                        }, 1000, 1000)
                                    }
                                    else {
                                        Handler(Looper.getMainLooper()).post {
//                                                if (isRecording) {
//                                                    isRecording = false
//                                                    roomId = this@DDPlayer.roomId
//                                                }
                                            player?.play()
                                        }
                                    }
                                }
                            })
                        }


                        OkHttpClient().newCall(
                                Request.Builder().headers(liveHeaders.build()).url(url).build()
                                              ).enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                Log.d("debug54", "response onFailure")
                                e.printStackTrace()
                            }

                            override fun onResponse(call: Call, response: Response) {
                                Log.d("debug54", "response.code ${response.code}")
                                response.header("Content-Type", "")?.let { Log.d("debug54", it) }
//                                    if (response.code != 475) {
//                                        __handler.post {
//                                            isRecording = false
//                                            roomId = this@DDPlayer.roomId
//                                        }
//                                        return
//                                    }
                                try {
                                    val byteStream = response.body!!.byteStream()
                                    val dir =
                                            File("${Environment.getExternalStorageDirectory().path}/DDPlayer/Records/$roomId/")
                                    if (!dir.exists()) dir.mkdirs()

                                    val cacheFile = File(dir, "$startTime.flv")
                                    val outputStream = FileOutputStream(cacheFile)

                                    var len: Int
                                    var loaded = false

                                    val buf = ByteArray(1024 * 1024)
                                    while (true) {
                                        len = byteStream.read(buf)
                                        if (len == -1) break

                                        total += len
                                        outputStream.write(buf, 0, len)
                                        outputStream.flush()

                                        if (!loaded) {
                                            loaded = true
                                            myHandler.post {
                                                player!!.setMediaItem(MediaItem.fromUri(cacheFile.toUri()))
                                            }
                                        }

                                        if (!isRecording) break
                                    }
                                    myHandler.post {
                                        player?.stop()
                                        roomId = this@DDPlayer.roomId
                                        Toast.makeText(
                                                context,
                                                "录像已保存${cacheFile.path}",
                                                Toast.LENGTH_SHORT
                                                      ).show()
                                    }
                                    outputStream.close()

                                }
                                catch (e: Exception) {
                                    e.printStackTrace()
                                    if (isRecording) {
                                        isRecording = false
                                        myHandler.post {
                                            player?.stop()
                                            roomId = this@DDPlayer.roomId
                                        }
                                    }
                                }
                            }

                        })

                    }
                }
            }
        })
    }

    fun refreshPlayer(msg: String) {
        // 重新加载播放器
        addMsg("${msg}，自动刷新")
        this.roomId = roomId
//        initPlayer()
//        getBasicinfo()
        return
//        player!!.stop()
//        OkHttpClient().newCall(
//                Request.Builder()
//                    .url("https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$roomId&qn=$qn&platform=h5")
//                    .headers(headers).build()
//                              ).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                val msg0 = "Request failed: $e"
//                Log.d("Exception", msg0)
//                addMsg(msg0)
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                if (!response.isSuccessful) {
//                    val msg0 = "Request failed with code: ${response.code}"
//                    Log.d("Exception", msg0)
//                    addMsg(msg0)
//                    return
//                }
//                response.body?.let { it2 ->
//                    try {
//                        val newurl =
//                                JSONObject(it2.string()).getJSONObject("data").getJSONArray("durl")
//                                    .getJSONObject(0).getString("url")
//                        if (newurl.isEmpty()) return
//                        myHandler.post {
//                            player!!.setMediaItem(MediaItem.fromUri(newurl))
//                            player!!.playWhenReady = true
//                            player!!.prepare()
//                        }
//                    }
//                    catch (e: Exception) {
//                        val msg0 = "Request failed: $e"
//                        Log.d("Exception", msg0)
//                        addMsg(msg0)
//                    }
//                }
//            }
//        })
    }

    var reconnecting = false
    fun connectDanmu() {
        // 连接弹幕
        socket = OkHttpClient.Builder().build()
            .newWebSocket(Request.Builder().url("wss://${host}:2245/sub")
                              .headers(liveHeaders.build()).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    super.onOpen(webSocket, response)
                    Log.d("danmu", "open")

                    // 连接成功，发送加入直播间的请求
//                val req = "{\"roomid\":$roomId}"
                    fun encode(op: Int, msg: String): ByteArray {
                        val body = msg.toByteArray(Charsets.UTF_8)
                        val headerLength = 16
                        val packetLength = headerLength + body.size
                        val ver = 1
                        val seq = 1

                        val buffer = ByteBuffer.allocate(packetLength)
                            .putInt(packetLength) // Packet length
                            .putShort(headerLength.toShort()) // Header length
                            .putShort(ver.toShort()) // Protocol version
                            .putInt(op) // Operation
                            .putInt(seq) // Sequence ID

                        return buffer.put(body).array()
                    }

                    val reqJson = JSONObject()
                    reqJson.put("roomid", roomId!!.toInt())
                    reqJson.put("protover", 3)
                    reqJson.put("uid", 0)
                    reqJson.put("platform", "web")
                    reqJson.put("type", 2)
                    reqJson.put("key", token)

                    val req = reqJson.toString()
                    Log.d("danmu", "req $req")

                    val payload = encode(7, req)

                    socket?.send(payload.toByteString())

                    // 开始心跳包发送
                    socketTimer = Timer()
                    socketTimer!!.schedule(object : TimerTask() {
                        override fun run() {
                            Log.d("danmu", "heartbeat")
//                        val obj = "[object Object]"
                            val heartbeat = encode(2, "")
//                        byteArray.addAll(obj.toByteArray().toList())
//                        socket?.send(
//                            byteArray.toByteArray().toByteString()
//                        )
                            socket?.send(heartbeat.toByteString())
                        }

                    }, 0, 30000)
                }

                override fun onMessage(
                        webSocket: WebSocket, bytes: ByteString
                                      ) {
                    super.onMessage(webSocket, bytes)
                    val byteArray = bytes.toByteArray()
//                Log.d("danmu", bytes.hex())
                    if (!reconnecting && byteArray[11] == 8.toByte()) {
                        myHandler.post {
                            addMsg("[系统] 已连接弹幕")
                        }
                    }
                    reconnecting = false
                    if (byteArray[7] == 3.toByte() || byteArray[7] == 2.toByte()) {

                        // 解压
                        val bis = ByteArrayInputStream(
                                byteArray, 16, byteArray.size - 16
                                                      )
                        var iis: InputStream? = null
                        if (byteArray[7] == 3.toByte()) {
                            iis = BrotliInputStream(bis)
                        }
                        else if (byteArray[7] == 2.toByte()) {
                            iis = InflaterInputStream(bis)
                        }
                        val buf = ByteArray(1024)

                        val bos = ByteArrayOutputStream()

                        if (iis == null) return

                        while (true) {
                            val c = iis.read(buf)
                            if (c == -1) break
                            bos.write(buf, 0, c)
                        }
                        bos.flush()
                        iis.close()

                        val unzipped = bos.toByteArray()

                        // 解压后是多条json连在一条字符串里，可根据每一条json前面16个字节的头，获取到每条json的长度
                        var len = 0
                        try {
                            while (len < unzipped.size) {
                                var b2 = unzipped[len + 2].toInt()
                                if (b2 < 0) b2 += 256
                                var b3 = unzipped[len + 3].toInt()
                                if (b3 < 0) b3 += 256

                                val nextLen = b2 * 256 + b3
//                                Log.d("danmu", "$nextLen = $b2 *256 + $b3 / $len / ${unzipped.size}")
                                val jstr = String(
                                        unzipped, len + 16, nextLen - 16, Charsets.UTF_8
                                                 )
//                            Log.d("danmu", jstr)
                                val jobj = JSONObject(jstr)
                                val cmd = jobj.getString("cmd")
                                if (cmd.startsWith("DANMU_MSG")) {
                                    var emojiUrl: String? = null
                                    try {
                                        emojiUrl = jobj.getJSONArray("info").getJSONArray(0)
                                            .getJSONObject(13).getString("url")
                                    }
                                    catch (e0: Exception) {
//                                    e0.printStackTrace()
                                    }
                                    val danmu = jobj.getJSONArray("info").getString(1)

//                                Log.d("danmu", "$roomId $danmu")
                                    myHandler.post {
                                        addMsg(danmu, emojiUrl)
                                    }
                                    if (isRecording) {
                                        try {
                                            val dir =
                                                    File("${Environment.getExternalStorageDirectory().path}/DDPlayer/Records/$roomId/")
                                            if (!dir.exists()) dir.mkdirs()

                                            val cacheFile = File(dir, "$startTime-danmu.txt")
                                            val writer = FileWriter(cacheFile, true)
                                            writer.write(
                                                    "${
                                                        RecordingUtils.minuteString(
                                                                recordingDurationLong
                                                                                   )
                                                    } $danmu\n"
                                                        )
                                            writer.close()
                                        }
                                        catch (e: Exception) {
                                            Log.d("Exception", "Failed: $e")
                                        }
                                    }
                                }
                                else if (cmd.startsWith("SUPER_CHAT_MESSAGE")) {
                                    Log.d("SC", jobj.toString())
                                    val danmu = jobj.getJSONObject("data").getString("message")
                                    myHandler.post {
                                        addMsg("[SC] $danmu")
                                    }
                                    if (isRecording) {
                                        try {
                                            val dir =
                                                    File("${Environment.getExternalStorageDirectory().path}/DDPlayer/Records/$roomId/")
                                            if (!dir.exists()) dir.mkdirs()

                                            val cacheFile = File(dir, "$startTime-danmu.txt")
                                            val writer = FileWriter(cacheFile, true)
                                            writer.write(
                                                    "${
                                                        RecordingUtils.minuteString(
                                                                recordingDurationLong
                                                                                   )
                                                    } [SC] $danmu\n"
                                                        )
                                            writer.close()
                                        }
                                        catch (e: Exception) {
                                            Log.d("Exception", "Failed: $e")
                                        }
                                    }
                                }

                                len += nextLen
                            }
                        }
                        catch (e: Exception) {
//                            Log.d("danmu", e.toString() + " " + e.message)
//                            e.printStackTrace()
                        }

                    }

                }

                override fun onFailure(
                        webSocket: WebSocket, t: Throwable, response: Response?
                                      ) {
                    super.onFailure(webSocket, t, response)
                    Log.d("danmu", "$roomId fail ${t.message}")
                    t.printStackTrace()
//                socket?.cancel()
//                socket?.close(4999, "failure")
//                reconnecting = true
//                connectDanmu()

                    myHandler.post {
                        addMsg("[系统] 弹幕可能已断开，请刷新")
                    }
                }

                override fun onClosing(
                        webSocket: WebSocket, code: Int, reason: String
                                      ) {
                    super.onClosing(webSocket, code, reason)
                    Log.d("danmu", "closing")

                }

                override fun onClosed(
                        webSocket: WebSocket, code: Int, reason: String
                                     ) {
                    super.onClosed(webSocket, code, reason)
                    Log.d("danmu", "close")
//                __handler.post {
//                    if (danmuList.count() > 20) {
//                        danmuList.removeAt(0)
//                    }
//                    danmuList.add("[系统] 弹幕已断开，请刷新")
//                    danmuListViewAdapter.notifyDataSetInvalidated()
//                    danmuListView.setSelection(danmuListView.bottom)
//
//                    if (interpreterList.count() > 20) {
//                        interpreterList.removeAt(0)
//                    }
//                    interpreterList.add("[系统] 弹幕已断开，请刷新")
//                    interpreterViewAdapter.notifyDataSetInvalidated()
//                    interpreterListView.setSelection(interpreterListView.bottom)
//                }
                }
            })
    }

    fun checkAndToastCellular() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            cm?.run {
                cm.getNetworkCapabilities(cm.activeNetwork)?.run {
                    if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        Log.d("checkAndToastCellular", "cellular")
                        Toast.makeText(context, "正在使用流量数据，请注意消耗", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
        }

    }

    // 宽高变化时调用，调整工具条，使得按钮隐藏，不超出宽度
    fun adjustControlBar() {
        Log.d("ddplayer", "width $width ${context.resources.displayMetrics.density}")
        if (width < context.resources.displayMetrics.density * 30 * 5) {
            refreshBtn.visibility = GONE
            volumeBtn.visibility = GONE
            danmuBtn.visibility = GONE
            qnBtn.visibility = GONE
            isHiddenBarBtns = true
        }
        else {
            refreshBtn.visibility = VISIBLE
            volumeBtn.visibility = VISIBLE
            danmuBtn.visibility = VISIBLE
            qnBtn.visibility = VISIBLE
            isHiddenBarBtns = false
        }
    }

    // 每当修改了播放器设置，做出相应的界面改变，然后保存设置
    fun notifyPlayerOptionsChange() {
//        volumeSlider.progress = (playerOptions.volume * 100f).roundToInt()
        player?.volume = if (isGlobalMuted) 0f else playerOptions.volume

        danmuView.visibility = if (playerOptions.isDanmuShow) VISIBLE else GONE
        danmuListView.visibility = if (playerOptions.interpreterStyle == 2) GONE else VISIBLE
        interpreterListView.visibility = if (playerOptions.interpreterStyle == 0) GONE else VISIBLE
//        danmuListView.invalidateViews()
//        interpreterListView.invalidateViews()
        danmuListViewAdapter.notifyDataSetInvalidated()
        interpreterViewAdapter.notifyDataSetInvalidated()

//        app:layout_constraintBottom_toBottomOf="parent"
//        app:layout_constraintEnd_toEndOf="parent"
//        app:layout_constraintStart_toStartOf="parent"
//        app:layout_constraintTop_toTopOf="parent"
//        app:layout_constraintHeight_percent=".8"
//        app:layout_constraintHorizontal_bias="0"
//        app:layout_constraintVertical_bias="0"
//        app:layout_constraintWidth_percent=".2"
        val layoutParams = danmuView.layoutParams as LayoutParams
        layoutParams.horizontalBias =
                if (playerOptions.danmuPosition == 0 || playerOptions.danmuPosition == 1) 0f else 1f
        layoutParams.verticalBias =
                if (playerOptions.danmuPosition == 0 || playerOptions.danmuPosition == 2) 0f else 1f
        layoutParams.matchConstraintPercentWidth = playerOptions.danmuWidth
        layoutParams.matchConstraintPercentHeight = playerOptions.danmuHeight

        danmuView.layoutParams = layoutParams

        val recordingViewLayoutParams = recordingView.layoutParams as LayoutParams
        recordingViewLayoutParams.horizontalBias =
                if (playerOptions.danmuPosition == 0 || playerOptions.danmuPosition == 1) 1f else 0f
        recordingView.layoutParams = recordingViewLayoutParams

        val jstr = Gson().toJson(playerOptions)
        Log.d("playeroptions", "${this.playerId} $jstr")
        context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
            this.putString("opts${this@DDPlayer.playerId}", jstr).apply()
        }
    }

    // 显示工具条，然后自动隐藏
    fun showControlBar() {
        controlBar.visibility = VISIBLE
        hideControlTimer?.cancel()
        hideControlTimer = null
        hideControlTimer = Timer()
        hideControlTimer!!.schedule(object : TimerTask() {
            override fun run() {
//                if (!volumeAdjusting) {
//                    Handler(Looper.getMainLooper()).post {
//                        controlBar.visibility = INVISIBLE
//                        volumeBar.visibility = INVISIBLE
//                    }
//
//                }
                Handler(Looper.getMainLooper()).post {
                    controlBar.visibility = INVISIBLE
                }
                hideControlTimer = null
            }
        }, 5000)
    }

//    fun startRecording() {
//        recordingView.visibility = VISIBLE
//        recordingDuration.text = "0:00"
//        recordingSize.text = "0K"
//        isRecording = true
//        roomId = roomId
//    }

//    fun endRecording() {
//        recordingView.visibility = GONE
//        isRecording = false
//        recordingTimer?.cancel()
//        recordingTimer = null
//        roomId = roomId
//    }
}
