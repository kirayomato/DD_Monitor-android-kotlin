package com.hyc.dd_monitor.views

import android.content.Context
import android.util.Log
import android.view.DragEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.children
import com.hyc.dd_monitor.R

class DDLayout(context: Context?) : LinearLayout(context) {
    var layoutId : Int = 4
        set(value) {
            field = value
            reloadLayout()
        }

    var players: ArrayList<DDPlayer>

    var onCardDropListener: (() -> Unit)? = null

    var layoutPlayerCount = 0

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
//        this.layoutId = 2


        this.players = ArrayList()

        for (i in 0..8) {
            val p = DDPlayer(context!!, i)
            p.onDragAndDropListener = { drag, drop ->
                Log.d("drop", "drag drop $drag $drop")
                val dragViewId = context.resources.getIdentifier(
                    "dd_layout_${drag+1}",
                    "id",
                    context.packageName
                )
                val dropViewId = context.resources.getIdentifier(
                    "dd_layout_${drop+1}",
                    "id",
                    context.packageName
                )
                val dragView = stackview?.findViewById<LinearLayout>(dragViewId)
                val dropView = stackview?.findViewById<LinearLayout>(dropViewId)
                (players[drop].parent as ViewGroup?)?.removeView(players[drop])
                dragView?.addView(players[drop])
                (players[drag].parent as ViewGroup?)?.removeView(players[drag])
                dropView?.addView(players[drag])
                players[drag].playerId = drop
                players[drop].playerId = drag

                val temp = players[drag]
                players[drag] = players[drop]
                players[drop] = temp

                post {
                    players[drag].adjustControlBar()
                    players[drop].adjustControlBar()
                }

                val volume2 = players[drop].playerOptions.volume

                players[drop].playerOptions.volume = players[drag].playerOptions.volume
                players[drop].notifyPlayerOptionsChange()

                players[drag].playerOptions.volume = volume2
                players[drag].notifyPlayerOptionsChange()

                context.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE).edit {
                    this.putString("roomId$drop", players[drop].roomId).apply()
                    this.putString("roomId$drag", players[drag].roomId).apply()
                }
            }
            p.onCardDropListener = {
                onCardDropListener?.invoke()
            }
            this.players.add(p)
        }

//        reloadLayout()

        context?.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE)?.getInt("layout", 4)?.let {
            this.layoutId = it
        }
    }

    var stackview: View? = null

    fun reloadLayout() {
        if (stackview != null) {
            removeView(stackview)
        }

        Log.d("ffffff", "dd_layout_$layoutId")
        val resId = context.resources.getIdentifier("dd_layout_$layoutId", "layout", context.packageName)
        stackview = inflate(context, resId, null)
        stackview?.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        addView(stackview)

        layoutPlayerCount = 0

        for (i in 1..9) {
            val layoutId = context.resources.getIdentifier("dd_layout_$i", "id", context.packageName)
            val v = stackview?.findViewById<LinearLayout>(layoutId)
            val p = players[i-1]
            (p.parent as ViewGroup?)?.removeView(p)
            v?.addView(p)

            if (v != null) {
                val roomId = context?.getSharedPreferences("sp", AppCompatActivity.MODE_PRIVATE)?.getString("roomId${i-1}", null)
                p.roomId = roomId
                post {
                    p.adjustControlBar()
                }
//                p.adjustControlBar()
                layoutPlayerCount += 1
            }else{
                p.roomId = null
            }
        }


//        val v1 = findViewById<LinearLayout>(R.id.dd_layout_1)
//        val v2 = findViewById<LinearLayout>(R.id.dd_layout_2)
//
//        v1?.setOnDragListener { view, dragEvent ->
//            if (dragEvent.action == DragEvent.ACTION_DROP) {
//                Log.d("drop", "v1 " + dragEvent.clipData.getItemAt(0))
//            }
//            return@setOnDragListener true
//        }
//
//
//        v2?.setOnDragListener { view, dragEvent ->
//            if (dragEvent.action == DragEvent.ACTION_DROP) {
//                Log.d("drop", "v2 " + dragEvent.clipData.getItemAt(0))
//            }
//            return@setOnDragListener true
//        }
    }
}