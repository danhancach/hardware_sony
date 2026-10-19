package com.sonyericsson.soundenhancement.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sonyericsson.soundenhancement.R
import com.sonyericsson.soundenhancement.backend.EffectPriorityManager
import com.sonyericsson.soundenhancement.ui.adapter.AppPriorityAdapter
import com.xperia.settings.ui.XperiaCollapsingToolbarBaseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Man hinh quan ly danh sach ung dung uu tien hieu ung (Effect Priority)
class EffectPriorityActivity : XperiaCollapsingToolbarBaseActivity() {

    private lateinit var priorityManager: EffectPriorityManager
    private lateinit var adapter: AppPriorityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.sound_enhance_effect_priority_title)
        setContentView(R.layout.activity_effect_priority)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_apps)
        applyScrollableBottomInsets(recyclerView, extraBottomDp = 0)

        priorityManager = EffectPriorityManager(this)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = AppPriorityAdapter(emptyList()) { item, enabled ->
            priorityManager.toggleAppPriority(item.packageName, enabled)
        }
        recyclerView.adapter = adapter

        val searchEditText = findViewById<EditText>(R.id.et_search_apps)
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val apps = priorityManager.loadAllPriorityApps()
            withContext(Dispatchers.Main) {
                adapter.updateList(apps)
            }
        }
    }
}
