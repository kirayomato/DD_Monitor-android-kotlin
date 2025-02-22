package com.hyc.dd_monitor.views

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.hyc.dd_monitor.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern
import com.hyc.dd_monitor.headers

class UidImportDialog(context: Context) : Dialog(context) {

    var result = mutableListOf<JSONObject>()
    var mids = mutableListOf<Int>()

    val handler = Handler(Looper.getMainLooper())

    lateinit var adapter: BaseAdapter

    var selectedSet: MutableSet<String> = mutableSetOf()

    var onImportFinishedListener: ((toImport: Set<String>) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_uidimport)


        adapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return result.count()
            }

            override fun getItem(p0: Int): Any {
                return p0
            }

            override fun getItemId(p0: Int): Long {
                return p0.toLong()
            }

            override fun getView(p0: Int, p1: View?, p2: ViewGroup?): View {
                val view = p1 ?: View.inflate(context, R.layout.list_item_up, null)

                val cover = view.findViewById<ImageView>(R.id.up_cover_image)
                val face = view.findViewById<ImageView>(R.id.up_face_image)
                val uname = view.findViewById<TextView>(R.id.up_uname_textview)
                val title = view.findViewById<TextView>(R.id.up_title_textview)
//                val shadow = view.findViewById<ImageView>(R.id.shadow_imageview)

                val isLiveCover = view.findViewById<TextView>(R.id.up_islive_cover)

                val checkbox = view.findViewById<FrameLayout>(R.id.up_checkbox)
//                checkbox.visibility = View.VISIBLE
//                checkbox.setOnCheckedChangeListener { compoundButton, b ->
//
//                }

                cover.setImageDrawable(null)
                face.setImageDrawable(null)
//                shadow.setImageDrawable(null)
                uname.text = ""
                title.text = ""
                uname.setBackgroundColor(Color.BLACK)
                title.setBackgroundColor(Color.BLACK)


                val upInfo = result[p0]

                checkbox.visibility = if (selectedSet.contains(
                                upInfo.getInt("room_id").toString()
                                                              )
                ) View.VISIBLE
                else View.GONE

                try {

//                    Picasso.get().load(upInfo?.faceImageUrl).transform(RoundImageTransform()).into(shadow)
                    uname.text = upInfo.getString("uname")
                    uname.setBackgroundColor(Color.TRANSPARENT)
                    title.text = upInfo.getString("title")
                    title.setBackgroundColor(Color.TRANSPARENT)

                    if (upInfo.getInt("live_status") == 1) {
                        isLiveCover.visibility = View.GONE
                    }
                    else {
                        isLiveCover.visibility = View.VISIBLE
                        isLiveCover.text = "未开播"
                    }
                    Glide.with(context).load(upInfo.getString("face")).circleCrop().into(face)
                    Glide.with(context).load(upInfo.getString("keyframe")).into(cover)

                }
                catch (e: Exception) {

                }

                return view
            }

        }

        val uidGridView = findViewById<GridView>(R.id.uid_gridview)
        uidGridView.adapter = adapter

        val selectionCountText = findViewById<TextView>(R.id.selection_count_textview)
        val importBtn = findViewById<Button>(R.id.import_btn)

        val uidText = findViewById<EditText>(R.id.uid_edittext)

        findViewById<Button>(R.id.submit_btn).setOnClickListener {
            val uid = uidText.text.toString()

            if (Pattern.compile("\\d+").matcher(uid).matches()) {
                Log.d("uid", uid.toString())
                mids.removeAll(mids)
                result.removeAll(result)
                adapter.notifyDataSetInvalidated()
                selectedSet.removeAll(selectedSet)
                selectionCountText.text = "已选0项"
                importBtn.isEnabled = false

                Toast.makeText(context, "查询中", Toast.LENGTH_SHORT).show()
                loadMids(uid, 1)
            }
            else {
                Toast.makeText(context, "无效的uid", Toast.LENGTH_SHORT).show()
            }
        }

        uidGridView.setOnItemClickListener { adapterView, view, i, l ->
            val roomId = result[i].getInt("room_id").toString()
            if (selectedSet.contains(roomId)) {
                selectedSet.remove(roomId)
            }
            else {
                selectedSet.add(roomId)
            }
            Log.d("uid", selectedSet.size.toString())
            selectionCountText.text = "已选${selectedSet.size}项"
            importBtn.isEnabled = selectedSet.isNotEmpty()

            adapter.notifyDataSetInvalidated()
        }

        importBtn.setOnClickListener {
            var uplist = mutableListOf<String>()
            context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE)
                .getString("uplist", "")?.let {
                    uplist = it.split(" ").toMutableList()
                    Log.d("uplist", it)
                }
            if (uplist.count() == 0 || uplist[0].isEmpty()) {
                uplist = mutableListOf()
            }

            for (up in uplist) {
                if (selectedSet.contains(up)) {
                    selectedSet.remove(up)
                }
            }

//            uplist.addAll(selectedSet)

            onImportFinishedListener?.invoke(selectedSet)



            dismiss()
        }

        findViewById<Button>(R.id.cancel_button).setOnClickListener {
            dismiss()
        }
    }

    fun loadMids(uid: String, pn: Int) {
        Log.d("uid", "loadmids")
        OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.bilibili.com/x/relation/followings?vmid=$uid&pn=$pn&ps=50&order=desc&jsonp=jsonp")
                    .headers(headers).build()
                              ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let {
                    try {
                        val jo = JSONObject(it.string())
                        Log.d("uid", jo.toString())
                        val resData = jo.optJSONObject("data")
                        if (resData == null) {
                            val errMsg = jo.getString("message")
                            handler.post {
                                Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                            }
                            return
                        }
                        val list = resData.getJSONArray("list")
                        if (list.length() > 0) {
                            for (i in 0 until list.length()) {
                                mids.add(list.getJSONObject(i).getInt("mid"))
                            }
                            loadMids(uid, pn + 1)
                        }
                        else {
                            loadRoomInfo()
                        }
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                        if (mids.count() == 0) {
                            handler.post {
                                Toast.makeText(context, "查询uid失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else {
                            loadRoomInfo()
                        }
                    }

                }

            }

        })
    }

    fun loadRoomInfo() {
        val body = Gson().toJson(mapOf(Pair("uids", mids)))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids")
                    .method("POST", body).headers(headers).build()
                              ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let {
                    try {
                        val jo = JSONObject(it.string())
                        val data = jo.getJSONObject("data")
                        for (k in data.keys()) {
                            result.add(data.getJSONObject(k))
                        }
                        result.sortByDescending { o ->
                            o.getInt("live_status") == 1
                        }
                        handler.post {
                            adapter.notifyDataSetInvalidated()
                        }
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                        handler.post {
                            Toast.makeText(context, "查询uid失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

            }

        })
    }
}