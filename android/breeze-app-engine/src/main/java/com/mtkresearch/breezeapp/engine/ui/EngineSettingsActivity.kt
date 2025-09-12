package com.mtkresearch.breezeapp.engine.ui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.mtkresearch.breezeapp.engine.BreezeAppEngineService
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.ReloadResult
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.RunnerManager
import kotlinx.coroutines.launch

/**
 * Engine Settings Activity
 * 
 * Provides a UI for configuring BreezeApp Engine runners and their parameters.
 * This activity is standalone and managed by the Engine app itself.
 * 
 * Features:
 * - Runner selection by capability
 * - Dynamic parameter configuration based on ParameterSchema
 * - Settings persistence
 * - Real-time validation
 */
class EngineSettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "EngineSettingsActivity"
    }
    
    // UI Components
    private lateinit var tabLayout: TabLayout
    private lateinit var tvCapabilityLabel: TextView
    private lateinit var spinnerRunners: Spinner
    private lateinit var tvRunnerDescription: TextView
    private lateinit var chipSupported: TextView
    private lateinit var chipNotSupported: TextView
    private lateinit var cardParameters: LinearLayout
    private lateinit var tvParametersHint: TextView
    private lateinit var containerParameters: LinearLayout
    private lateinit var btnSave: Button
    
    // State
    private var currentCapability: CapabilityType = CapabilityType.LLM
    private var currentSettings: EngineSettings = EngineSettings.default()
    private var currentRunnerParameters: MutableMap<String, Any> = mutableMapOf()
    private var availableRunners: Map<CapabilityType, List<RunnerInfo>> = emptyMap()
    private var runnerParameters: Map<String, List<ParameterSchema>> = emptyMap()
    
    // Direct RunnerManager access
    private var runnerManager: RunnerManager? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_engine_settings)
        
        setupToolbar()
        initViews()
        setupTabs()
        initializeRunnerManager()
        loadSettings()
        setupEventListeners()
        setupObservers()
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Engine Settings"
        
        // Set navigation icon tint programmatically
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
    }
    
    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        tvCapabilityLabel = findViewById(R.id.tvCapabilityLabel)
        spinnerRunners = findViewById(R.id.spinnerRunners)
        tvRunnerDescription = findViewById(R.id.tvRunnerDescription)
        chipSupported = findViewById(R.id.chipSupported)
        chipNotSupported = findViewById(R.id.chipNotSupported)
        cardParameters = findViewById(R.id.cardParameters)
        tvParametersHint = findViewById(R.id.tvParametersHint)
        containerParameters = findViewById(R.id.containerParameters)
        btnSave = findViewById(R.id.btnSave)
    }
    
    private fun setupTabs() {
        val capabilities = listOf(
            "LLM" to CapabilityType.LLM,
            "ASR" to CapabilityType.ASR,
            "TTS" to CapabilityType.TTS,
            "VLM" to CapabilityType.VLM,
            "Guardian" to CapabilityType.GUARDIAN
        )
        
        capabilities.forEach { (title, capability) ->
            val tab = tabLayout.newTab().setText(title)
            tabLayout.addTab(tab)
        }
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab != null) {
                    currentCapability = capabilities[tab.position].second
                    updateCapabilityUI()
                    loadRunnersForCapability()
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // Select first tab by default
        if (tabLayout.tabCount > 0) {
            tabLayout.selectTab(tabLayout.getTabAt(0))
        }
    }
    
    private fun initializeRunnerManager() {
        try {
            runnerManager = BreezeAppEngineService.getRunnerManager()
            if (runnerManager == null) {
                Log.e(TAG, "RunnerManager not available - starting service")
                
                // Start the engine service if not running
                val serviceIntent = android.content.Intent(this, com.mtkresearch.breezeapp.engine.BreezeAppEngineService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                
                // Wait and retry
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    runnerManager = BreezeAppEngineService.getRunnerManager()
                    if (runnerManager == null) {
                        Toast.makeText(this, "Engine service failed to initialize", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        loadSettings()
                        updateCapabilityUI()
                        loadRunnersForCapability()
                        setupObservers()
                    }
                }, 2000)
                
                Toast.makeText(this, "Starting engine service...", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing RunnerManager", e)
            Toast.makeText(this, "Error initializing engine: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun loadSettings() {
        try {
            // Load current settings from RunnerManager
            currentSettings = runnerManager?.getCurrentSettings() ?: EngineSettings.default()
            
            // Load available runners and their parameters from the service
            loadAvailableRunners()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading settings", e)
            Toast.makeText(this, "Error loading settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun loadAvailableRunners() {
        try {
            // Get available runners from RunnerManager
            val allRunners = runnerManager?.getAllRunners() ?: emptyList()
            
            // Group runners by capability
            availableRunners = allRunners
                .flatMap { runner ->
                    runner.getCapabilities().map { capability ->
                        capability to runner.getRunnerInfo()
                    }
                }
                .groupBy({ it.first }, { it.second })
            
            // Load parameter schemas for each runner
            loadRunnerParameters()
            
            // Update UI
            loadRunnersForCapability()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading runners", e)
            Toast.makeText(this, "Error loading runners: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun loadRunnerParameters() {
        try {
            // Get parameter schemas for each runner from RunnerManager
            val allRunners = runnerManager?.getAllRunners() ?: emptyList()
            val runnerParams = mutableMapOf<String, List<ParameterSchema>>()
            
            for (runner in allRunners) {
                val runnerName = runner.getRunnerInfo().name
                val parameters = runnerManager?.getRunnerParameters(runnerName) ?: emptyList()
                runnerParams[runnerName] = parameters
            }
            
            runnerParameters = runnerParams
        } catch (e: Exception) {
            Log.e(TAG, "Error loading runner parameters", e)
        }
    }
    
    private fun loadRunnersForCapability() {
        val runners = availableRunners[currentCapability] ?: emptyList()
        val runnerNames = runners.map { it.name }
        
        // Create adapter for spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, runnerNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRunners.adapter = adapter
        
        // Set currently selected runner
        val selectedRunner = currentSettings.selectedRunners[currentCapability]
        if (selectedRunner != null) {
            val selectedIndex = runnerNames.indexOf(selectedRunner)
            if (selectedIndex >= 0) {
                spinnerRunners.setSelection(selectedIndex)
            }
        }
        
        // Update runner description and status
        updateRunnerInfo()
    }
    
    private fun updateCapabilityUI() {
        tvCapabilityLabel.text = "Select runner for ${currentCapability.name} capability:"
    }
    
    private fun updateRunnerInfo() {
        val selectedRunnerName = spinnerRunners.selectedItem?.toString()
        if (selectedRunnerName != null) {
            // Find runner info
            val runnerInfo = availableRunners[currentCapability]?.find { it.name == selectedRunnerName }
            
            if (runnerInfo != null) {
                tvRunnerDescription.text = runnerInfo.description
                chipSupported.visibility = View.VISIBLE
                chipNotSupported.visibility = View.GONE
            } else {
                tvRunnerDescription.text = "Runner information not available"
                chipSupported.visibility = View.GONE
                chipNotSupported.visibility = View.VISIBLE
            }
            
            // Load and display parameters for this runner
            loadRunnerParameters(selectedRunnerName)
        } else {
            tvRunnerDescription.text = "No runners available for this capability"
            chipSupported.visibility = View.GONE
            chipNotSupported.visibility = View.VISIBLE
            clearParameterViews()
        }
    }
    
    private fun loadRunnerParameters(runnerName: String) {
        val schemas = runnerParameters[runnerName] ?: emptyList()
        
        if (schemas.isEmpty()) {
            tvParametersHint.visibility = View.VISIBLE
            containerParameters.visibility = View.GONE
            clearParameterViews()
            return
        }
        
        tvParametersHint.visibility = View.GONE
        containerParameters.visibility = View.VISIBLE
        
        // Get current parameter values for this runner
        val currentValues = currentSettings.getRunnerParameters(runnerName)
        
        // Create parameter views - simplified implementation without DynamicParameterView
        val parameterViews = createSimpleParameterViews(schemas, currentValues)
        
        // Clear existing views and add new ones
        clearParameterViews()
        parameterViews.forEach { view ->
            containerParameters.addView(view)
        }
    }
    
    private fun clearParameterViews() {
        containerParameters.removeAllViews()
        currentRunnerParameters.clear()
    }
    
    private fun createSimpleParameterViews(
        schemas: List<ParameterSchema>,
        currentValues: Map<String, Any>
    ): List<android.view.View> {
        val views = mutableListOf<android.view.View>()
        
        schemas.forEach { schema ->
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }
            
            // Parameter label
            val label = TextView(this).apply {
                text = schema.name
                textSize = 14f
                setTextColor(android.graphics.Color.BLACK)
            }
            container.addView(label)
            
            // Parameter input based on type
            when (schema.type.toString().lowercase(java.util.Locale.ROOT)) {
                "float", "double", "number" -> {
                    val editText = android.widget.EditText(this).apply {
                        hint = "Enter ${schema.type}"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setText(currentValues[schema.name]?.toString() ?: schema.defaultValue?.toString() ?: "")
                        
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                try {
                                    val value = s.toString().toDoubleOrNull()
                                    if (value != null) {
                                        currentRunnerParameters[schema.name] = value
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Invalid number input for ${schema.name}")
                                }
                            }
                        })
                    }
                    container.addView(editText)
                }
                "int", "integer" -> {
                    val editText = android.widget.EditText(this).apply {
                        hint = "Enter integer"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        setText(currentValues[schema.name]?.toString() ?: schema.defaultValue?.toString() ?: "")
                        
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                try {
                                    val value = s.toString().toIntOrNull()
                                    if (value != null) {
                                        currentRunnerParameters[schema.name] = value
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Invalid integer input for ${schema.name}")
                                }
                            }
                        })
                    }
                    container.addView(editText)
                }
                "boolean" -> {
                    val switch = android.widget.Switch(this).apply {
                        text = "Enable ${schema.name}"
                        isChecked = currentValues[schema.name] as? Boolean ?: schema.defaultValue as? Boolean ?: false
                        
                        setOnCheckedChangeListener { _, isChecked ->
                            currentRunnerParameters[schema.name] = isChecked
                        }
                    }
                    container.addView(switch)
                }
                else -> {
                    val editText = android.widget.EditText(this).apply {
                        hint = "Enter ${schema.type}"
                        setText(currentValues[schema.name]?.toString() ?: schema.defaultValue?.toString() ?: "")
                        
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                currentRunnerParameters[schema.name] = s.toString()
                            }
                        })
                    }
                    container.addView(editText)
                }
            }
            
            views.add(container)
        }
        
        return views
    }
    
    private fun setupEventListeners() {
        spinnerRunners.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateRunnerInfo()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearParameterViews()
            }
        }
        
        btnSave.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun setupObservers() {
        runnerManager?.observeSettingsChanges { _, result ->
            runOnUiThread {
                handleReloadResult(result)
            }
        }
    }

    private fun handleReloadResult(result: ReloadResult) {
        val message = when (result) {
            is ReloadResult.Success -> "Settings saved and runners reloaded successfully."
            is ReloadResult.Failure -> "Settings saved, but failed to reload runners: ${result.error.message}"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                // Get currently selected runner
                val selectedRunnerName = spinnerRunners.selectedItem?.toString()
                if (selectedRunnerName != null) {
                    // Update settings with selected runner
                    currentSettings = currentSettings.withRunnerSelection(currentCapability, selectedRunnerName)
                    
                    // Update settings with runner parameters
                    if (currentRunnerParameters.isNotEmpty()) {
                        currentSettings = currentSettings.withRunnerParameters(selectedRunnerName, currentRunnerParameters.toMap())
                    }
                    
                    // Save settings using RunnerManager
                    runnerManager?.saveSettings(currentSettings)
                    
                    // Reset parameter values for next edit
                    currentRunnerParameters.clear()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving settings", e)
                Toast.makeText(this@EngineSettingsActivity, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}