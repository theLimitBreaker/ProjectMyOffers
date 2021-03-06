package com.intellyticshub.projectmyoffers.ui.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Observer
import com.google.android.material.tabs.TabLayout
import com.intellyticshub.projectmyoffers.R
import com.intellyticshub.projectmyoffers.data.Repository
import com.intellyticshub.projectmyoffers.data.entity.OfferModel
import com.intellyticshub.projectmyoffers.ui.adapters.PagerAdapter
import com.intellyticshub.projectmyoffers.utils.Constants
import com.intellyticshub.projectmyoffers.utils.OfferExtractor
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    var isActiveTab: Boolean = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initFields()
        checkFirstRun()
    }

    override fun onBackPressed() {

        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun checkFirstRun() {
        val sharedPrefs = getSharedPreferences(getString(R.string.shared_pref_key), Context.MODE_PRIVATE)
        val isFirstRun = sharedPrefs.getBoolean(getString(R.string.first_run), true)

        if (isFirstRun) {
            Handler().post { scanForOffers() }
            sharedPrefs.edit().putBoolean(getString(R.string.first_run), false).apply()
        }

    }

    private fun initFields() {
        progress_circular.visibility = View.GONE
        viewPager.adapter = PagerAdapter(supportFragmentManager, 2)
        viewPager.offscreenPageLimit = 1
        viewPager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))

        tvMarquee.isSelected = true

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) {}

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        isActiveTab = true
                        ivSearch.visibility = View.VISIBLE
                        viewPager.currentItem = 0
                        tvMarquee.visibility = View.VISIBLE
                        fab.setImageResource(R.drawable.ic_find)
                    }
                    1 -> {
                        isActiveTab = false
                        ivSearch.visibility = View.GONE
                        viewPager.currentItem = 1
                        tvMarquee.visibility = View.GONE
                        fab.setImageResource(R.drawable.ic_delete)
                    }
                }
            }
        })

        fab.setOnClickListener {
            if (isActiveTab) {
                scanForOffers()
            } else {
                deleteAllExpired()
            }
        }

        ViewCompat.setElevation(ivSearch, 100.0f)
        ViewCompat.setElevation(ivNavMenu, 100.0f)
        ivSearch.setOnClickListener {
            startActivity(Intent(this@MainActivity, SearchActivity::class.java))
        }

        ivNavMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.action_expire_today -> {
                    val intent = Intent(this, OffersInRangeActivity::class.java).apply {
                        putExtra("range", "today")
                    }
                    startActivity(intent)
                    true
                }
                R.id.action_expire_week -> {
                    val intent = Intent(this, OffersInRangeActivity::class.java).apply {
                        putExtra("range", "week")
                    }
                    startActivity(intent)
                    true
                }
                R.id.action_about_us -> {
                    val browseIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(Constants.contactWebsite)
                    }
                    startActivity(browseIntent)
                    true
                }
                R.id.action_feedback -> {
                    startActivity(Intent(this@MainActivity, FeedbackActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun startLoadingAnim() {
        fab.isEnabled = false
        viewPager.visibility = View.GONE
        tvMarquee.visibility = View.GONE
        progress_circular.visibility = View.VISIBLE
        progress_circular.animate()

    }

    private fun stopLoadingAnim() {
        fab.isEnabled = true
        tvMarquee.visibility = View.VISIBLE
        viewPager.visibility = View.VISIBLE
        progress_circular.visibility = View.GONE
    }


    private fun scanForOffers() {
        startLoadingAnim()

        val repository = Repository.getInstance(application)

        val sharedPrefs = getSharedPreferences(getString(R.string.shared_pref_key), Context.MODE_PRIVATE)
        var lastSmsTimeMillis = sharedPrefs.getLong(getString(R.string.last_sms_time_milllis), -1)
        val newOffers = ArrayList<OfferModel>()
        val executor = Executors.newSingleThreadExecutor()
        val scanTask = Callable {
            val smsURI = Uri.parse("content://sms/inbox")
            val cursor = contentResolver.query(
                smsURI, null, null, null, null
            )
            cursor?.run {

                val currTimeMillis = System.currentTimeMillis()
                var maxTimeMillis = lastSmsTimeMillis
                while (moveToNext()) {
                    val address = getString(getColumnIndexOrThrow("address"))
                    val smsBody = getString(getColumnIndexOrThrow("body"))
                    val timeInMillis = getLong(getColumnIndexOrThrow("date"))


                    if (timeInMillis > lastSmsTimeMillis) {
                        val offerExtractor = OfferExtractor(smsBody)
                        val offerCode = offerExtractor.extractOfferCode()
                        val offer = offerExtractor.extractOffer()
                        if (offerCode != "none" && offer != "none") {
                            val calendar = Calendar.getInstance().apply { setTimeInMillis(timeInMillis) }
                            val smsYear = calendar.get(Calendar.YEAR).toString()

                            val expiryDateInfo = offerExtractor.extractExpiryDate(smsYear)

                            val expiryDate = when {
                                expiryDateInfo.expiryDate == "findFromCurrTime" -> with(calendar) {
                                    val day = get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
                                    val month = (get(Calendar.MONTH) + 1).toString().padStart(2, '0')
                                    val year = get(Calendar.YEAR)
                                    "$day/$month/$year"
                                }
                                expiryDateInfo.expiryDate == "none" -> ""
                                else -> expiryDateInfo.expiryDate
                            }


                            val expiryTimeMillis =
                                when (expiryDateInfo.expiryTimeInMillis) {
                                    -2L -> {
                                        val oneDayExpiry = with(calendar) {
                                            set(Calendar.HOUR_OF_DAY, 23)
                                            set(Calendar.MINUTE, 30)
                                            calendar.timeInMillis
                                        }
                                        oneDayExpiry
                                    }
                                    Long.MAX_VALUE -> {
                                        calendar.timeInMillis + Constants.padExtraTime
                                    }
                                    else -> expiryDateInfo.expiryTimeInMillis
                                }

                            val newOffer = OfferModel(
                                offerCode = offerCode,
                                offer = offer,
                                vendor = address,
                                message = if (expiryTimeMillis >= currTimeMillis) smsBody else "",
                                expiryDate = expiryDate,
                                expiryTimeInMillis = expiryTimeMillis,
                                deleteMark = false
                            )

                            newOffers.add(newOffer)
                        }
                    }
                    if (maxTimeMillis < timeInMillis)
                        maxTimeMillis = timeInMillis
                }
                lastSmsTimeMillis = maxTimeMillis
            }

            cursor?.close()

            if (newOffers.isNotEmpty()) {
                sharedPrefs.edit().putLong(getString(R.string.last_sms_time_milllis), lastSmsTimeMillis).apply()
                repository.insertOffers(*newOffers.toTypedArray())
            }

            if (newOffers.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this, "No new  Offers found :(", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Handler().post {
            executor.submit(scanTask).get()
            stopLoadingAnim()
        }
    }


    private fun deleteAllExpired() {
        val repository = Repository.getInstance(application)
        var expiredList: List<OfferModel> = listOf()
        val expiredLive = repository.expiredOffers
        expiredLive.observe(this, Observer { it -> it.let { expiredList = it } })
        expiredLive.removeObservers(this)
        if (expiredList.isNotEmpty()) {
            val builder = AlertDialog.Builder(this)
                .setMessage("Delete All ?")
                .setPositiveButton("ok") { _, _ ->
                    repository.deleteOffers(*(expiredList.toTypedArray()))
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.offer_card_bg_solid)
            dialog.show()
        }
    }

}