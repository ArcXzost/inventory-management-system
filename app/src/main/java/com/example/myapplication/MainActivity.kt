package com.example.myapplication

import android.os.Bundle
import np.com.susanthapa.curved_bottom_navigation.CbnMenuItem
import np.com.susanthapa.curved_bottom_navigation.CurvedBottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: CurvedBottomNavigationView
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.bottomNav)
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        userId = intent.getStringExtra("USER_ID").toString()
        val menuItems = arrayOf(
            CbnMenuItem(R.drawable.inventory_dashboard, R.drawable.avd_inventory_dashboard),
            CbnMenuItem(R.drawable.inventory_orders, R.drawable.avd_inventory_orders),
            CbnMenuItem(R.drawable.inventory_analytics, R.drawable.avd_inventory_analytics),
            CbnMenuItem(R.drawable.inventory_account, R.drawable.avd_inventory_account)
        )
        bottomNav.setMenuItems(menuItems)

        // Load the default fragment (Dashboard) when the activity starts
        loadFragment(DashboardFragment())

        bottomNav.setOnMenuItemClickListener { _, position ->
            when (position) {
                0 -> loadFragment(DashboardFragment())
                1 -> loadFragment(OrdersFragment()) // Placeholder for orders fragment
                2 -> loadFragment(AnalyticsFragment()) // Placeholder for analytics fragment
                3 -> loadFragment(AccountFragment()) // Placeholder for account fragment
            }
        }
        initializeDatabase()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun initializeDatabase() {
        val databaseInitializer = DatabaseInitializer(this)
        lifecycleScope.launch {
            databaseInitializer.initializeDatabase()
        }
    }

    fun getUserId(): String = userId
}