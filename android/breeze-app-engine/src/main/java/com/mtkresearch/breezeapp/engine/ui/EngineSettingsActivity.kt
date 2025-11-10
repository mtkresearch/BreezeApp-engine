package com.mtkresearch.breezeapp.engine.ui

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.mtkresearch.breezeapp.engine.BreezeAppEngineService
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.model.CapabilityType
import com.mtkresearch.breezeapp.engine.model.EngineSettings
import com.mtkresearch.breezeapp.engine.model.ReloadResult
import com.mtkresearch.breezeapp.engine.model.ui.UnsavedChangesState
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.RunnerManager
import com.mtkresearch.breezeapp.engine.runner.core.SelectionOption
import com.mtkresearch.breezeapp.engine.runner.openrouter.models.ModelInfo
import com.mtkresearch.breezeapp.engine.runner.openrouter.models.OpenRouterModelFetcher
import com.mtkresearch.breezeapp.engine.ui.dialogs.showUnsavedChangesDialog
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

    /**
     * Enum representing the current state of the save operation.
     * Used for UI feedback and navigation blocking during async saves.
     */
    private enum class SaveOperationState {
        IDLE,           // No save in progress
        IN_PROGRESS,    // Save operation executing
        SUCCESS,        // Save completed successfully
        FAILED          // Save operation failed
    }
    
    // UI Components
    private lateinit var tabLayout: TabLayout
    private lateinit var cardPriceFilter: com.google.android.material.card.MaterialCardView
    private lateinit var seekBarPrice: SeekBar
    private lateinit var tvPriceLabel: TextView
    private lateinit var tvModelCount: TextView
    private lateinit var btnRefreshModels: Button
    private lateinit var tvCapabilityLabel: TextView
    private lateinit var spinnerRunners: Spinner
    private lateinit var tvRunnerDescription: TextView
    private lateinit var chipSupported: TextView
    private lateinit var chipNotSupported: TextView
    private lateinit var cardParameters: com.google.android.material.card.MaterialCardView
    private lateinit var tvParametersHint: TextView
    private lateinit var containerParameters: LinearLayout
    private lateinit var btnSave: Button
    private lateinit var progressOverlay: FrameLayout

    // State
    private var currentCapability: CapabilityType = CapabilityType.LLM
    private var currentSettings: EngineSettings = EngineSettings.default()
    private var currentRunnerParameters: MutableMap<String, Any> = mutableMapOf()
    private var availableRunners: Map<CapabilityType, List<RunnerInfo>> = emptyMap()
    private var runnerParameters: Map<String, List<ParameterSchema>> = emptyMap()

    // Unsaved changes tracking
    private val unsavedChangesState = UnsavedChangesState()
    private var navigationConfirmed = false

    // Direct RunnerManager access
    private var runnerManager: RunnerManager? = null

    // Model fetching (for OpenRouter LLM)
    private var allModels: List<ModelInfo> = emptyList()
    private var filteredModels: List<ModelInfo> = emptyList()
    private val modelFetcher by lazy {
        OpenRouterModelFetcher(
            prefs = getSharedPreferences("engine_settings_models", MODE_PRIVATE)
        )
    }
    
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            Log.d(TAG, "Back button pressed! Has unsaved changes: ${unsavedChangesState.hasAnyUnsavedChanges()}")

            if (unsavedChangesState.hasAnyUnsavedChanges()) {
                Log.d(TAG, "Showing unsaved changes dialog")
                showUnsavedChangesDialogAndNavigate()
            } else {
                Log.d(TAG, "No unsaved changes, allowing back navigation")
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

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

        // Setup unsaved changes detection
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
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
        cardPriceFilter = findViewById(R.id.cardPriceFilter)
        seekBarPrice = findViewById(R.id.seekBarPrice)
        tvPriceLabel = findViewById(R.id.tvPriceLabel)
        tvModelCount = findViewById(R.id.tvModelCount)
        btnRefreshModels = findViewById(R.id.btnRefreshModels)
        tvCapabilityLabel = findViewById(R.id.tvCapabilityLabel)
        spinnerRunners = findViewById(R.id.spinnerRunners)
        tvRunnerDescription = findViewById(R.id.tvRunnerDescription)
        chipSupported = findViewById(R.id.chipSupported)
        chipNotSupported = findViewById(R.id.chipNotSupported)
        cardParameters = findViewById(R.id.cardParameters)
        tvParametersHint = findViewById(R.id.tvParametersHint)
        containerParameters = findViewById(R.id.containerParameters)
        btnSave = findViewById(R.id.btnSave)
        progressOverlay = findViewById(R.id.progressOverlay)

        // Initialize Save button as disabled (no changes yet)
        btnSave.isEnabled = false

        // Setup price filter
        setupPriceFilter()
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
                    // Get the actual value being displayed (prioritize saved value, fallback to default)
                    val initialValue = currentValues[schema.name] ?: schema.defaultValue
                    var isInitializing = true

                    val editText = android.widget.EditText(this).apply {
                        hint = "Enter ${schema.type}"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

                        // Set initial value
                        setText(initialValue?.toString() ?: "")

                        // Add listener AFTER setText
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                // Skip the first call triggered by setText()
                                if (isInitializing) {
                                    isInitializing = false
                                    return
                                }

                                try {
                                    val value = s.toString().toDoubleOrNull()
                                    if (value != null) {
                                        currentRunnerParameters[schema.name] = value
                                        onParameterChanged(schema.name, value, initialValue)
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
                    val initialValue = currentValues[schema.name] ?: schema.defaultValue
                    var isInitializing = true

                    val editText = android.widget.EditText(this).apply {
                        hint = "Enter integer"
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        setText(initialValue?.toString() ?: "")

                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                if (isInitializing) {
                                    isInitializing = false
                                    return
                                }

                                try {
                                    val value = s.toString().toIntOrNull()
                                    if (value != null) {
                                        currentRunnerParameters[schema.name] = value
                                        onParameterChanged(schema.name, value, initialValue)
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
                    val initialValue = currentValues[schema.name] ?: schema.defaultValue
                    var isInitializing = true

                    val switch = android.widget.Switch(this).apply {
                        text = "Enable ${schema.name}"
                        isChecked = initialValue as? Boolean ?: false

                        setOnCheckedChangeListener { _, isChecked ->
                            if (isInitializing) {
                                isInitializing = false
                                return@setOnCheckedChangeListener
                            }

                            currentRunnerParameters[schema.name] = isChecked
                            onParameterChanged(schema.name, isChecked, initialValue)
                        }
                    }
                    container.addView(switch)
                }
                else -> {
                    // Check if this is a SelectionType parameter
                    if (schema.type is ParameterType.SelectionType) {
                        handleSelectionType(schema, currentValues, container)
                    } else {
                        // Default to text input for unknown types
                        val initialValue = currentValues[schema.name] ?: schema.defaultValue
                        var isInitializing = true

                        val editText = android.widget.EditText(this).apply {
                            hint = "Enter ${schema.type}"
                            setText(initialValue?.toString() ?: "")

                            addTextChangedListener(object : android.text.TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                override fun afterTextChanged(s: android.text.Editable?) {
                                    if (isInitializing) {
                                        isInitializing = false
                                        return
                                    }

                                    val value = s.toString()
                                    currentRunnerParameters[schema.name] = value
                                    onParameterChanged(schema.name, value, initialValue)
                                }
                            })
                        }
                        container.addView(editText)
                    }
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
                updatePriceFilterVisibility()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearParameterViews()
            }
        }

        btnSave.setOnClickListener {
            // Save without navigating away (stay in settings)
            saveSettingsWithoutNavigate()
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

    override fun finish() {
        if (unsavedChangesState.hasAnyUnsavedChanges() && !navigationConfirmed) {
            showUnsavedChangesDialogAndNavigate()
        } else {
            super.finish()
        }
    }

    private fun showUnsavedChangesDialogAndNavigate() {
        val dirtyRunners = unsavedChangesState.getDirtyRunners()
        showUnsavedChangesDialog(
            dirtyRunners = dirtyRunners,
            onSave = {
                saveSettingsAndNavigate()
            },
            onDiscard = {
                unsavedChangesState.clearAll()
                navigationConfirmed = true
                finish()
            }
        )
    }

    private fun saveSettingsAndNavigate() {
        showSaveProgress()

        lifecycleScope.launch {
            try {
                // Get currently selected runner name
                val selectedRunner = getSelectedRunnerName() ?: run {
                    hideSaveProgress()
                    return@launch
                }

                // Build updated settings with current parameters
                val updatedSettings = currentSettings.withRunnerParameters(
                    selectedRunner,
                    currentRunnerParameters
                )

                // Save via RunnerManager
                runnerManager?.saveSettings(updatedSettings)

                // Clear dirty state and navigate
                unsavedChangesState.clearAll()
                hideSaveProgress()
                navigationConfirmed = true
                finish()
            } catch (e: Exception) {
                hideSaveProgress()
                Log.e(TAG, "Failed to save settings", e)
                Toast.makeText(this@EngineSettingsActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveSettingsWithoutNavigate() {
        showSaveProgress()

        lifecycleScope.launch {
            try {
                // Get currently selected runner name
                val selectedRunner = getSelectedRunnerName() ?: run {
                    hideSaveProgress()
                    return@launch
                }

                // Build updated settings with current parameters
                val updatedSettings = currentSettings.withRunnerParameters(
                    selectedRunner,
                    currentRunnerParameters
                )

                // Save via RunnerManager
                runnerManager?.saveSettings(updatedSettings)

                // Clear dirty state but DON'T navigate
                unsavedChangesState.clearAll()
                hideSaveProgress()

                // Disable save button since no changes now
                btnSave.isEnabled = false
                onBackPressedCallback.isEnabled = false

                Toast.makeText(this@EngineSettingsActivity, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                hideSaveProgress()
                Log.e(TAG, "Failed to save settings", e)
                Toast.makeText(this@EngineSettingsActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSaveProgress() {
        progressOverlay.visibility = View.VISIBLE
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
    }

    private fun hideSaveProgress() {
        progressOverlay.visibility = View.GONE
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun onParameterChanged(parameterName: String, currentValue: Any?, originalValue: Any?) {
        val selectedRunner = getSelectedRunnerName() ?: return

        Log.d(TAG, "onParameterChanged: param=$parameterName, original=$originalValue, current=$currentValue, runner=$selectedRunner")

        // Normalize values for comparison (convert numeric types properly)
        val normalizedOriginal = when (originalValue) {
            is Number -> originalValue.toDouble()
            else -> originalValue
        }
        val normalizedCurrent = when (currentValue) {
            is Number -> currentValue.toDouble()
            else -> currentValue
        }

        // Track the change in unsaved changes state
        unsavedChangesState.trackChange(
            capability = currentCapability,
            runnerName = selectedRunner,
            parameterName = parameterName,
            originalValue = normalizedOriginal,
            currentValue = normalizedCurrent
        )

        // Update UI state based on dirty status
        val hasDirtyChanges = unsavedChangesState.hasAnyUnsavedChanges()
        Log.d(TAG, "Dirty state: $hasDirtyChanges (original=$normalizedOriginal, current=$normalizedCurrent)")

        onBackPressedCallback.isEnabled = hasDirtyChanges
        btnSave.isEnabled = hasDirtyChanges

        Log.d(TAG, "Save button enabled: ${btnSave.isEnabled}, Back callback enabled: ${onBackPressedCallback.isEnabled}")
    }

    private fun getSelectedRunnerName(): String? {
        val selectedRunner = availableRunners[currentCapability]?.getOrNull(spinnerRunners.selectedItemPosition)
        return selectedRunner?.name
    }

    /**
     * Handle SelectionType parameter - create dropdown
     */
    private fun handleSelectionType(
        schema: ParameterSchema,
        currentValues: Map<String, Any>,
        container: LinearLayout
    ) {
        val selectionType = schema.type as ParameterType.SelectionType
        val initialValue = currentValues[schema.name] ?: schema.defaultValue

        // Determine options: use fetched models for OpenRouter model parameter, otherwise use schema options
        val options = if (isOpenRouterRunner() && schema.name == "model" && filteredModels.isNotEmpty()) {
            // Use dynamically fetched models
            filteredModels.map { model ->
                SelectionOption(
                    key = model.id,
                    displayName = model.getShortDisplayName(),
                    description = model.description
                )
            }
        } else {
            // Use schema-defined options
            selectionType.options
        }

        // Create spinner (dropdown)
        val spinner = Spinner(this).apply {
            val displayNames = options.map { it.displayName }
            val adapter = ArrayAdapter(
                this@EngineSettingsActivity,
                android.R.layout.simple_spinner_item,
                displayNames
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            this.adapter = adapter

            // Set initial selection
            val initialKey = initialValue?.toString()
            val initialIndex = options.indexOfFirst { it.key == initialKey }
            if (initialIndex >= 0) {
                setSelection(initialIndex)
            }

            // Listen for selection changes
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                var isInitializing = true

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isInitializing) {
                        isInitializing = false
                        return
                    }

                    val selectedOption = options[position]
                    currentRunnerParameters[schema.name] = selectedOption.key
                    onParameterChanged(schema.name, selectedOption.key, initialKey)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        container.addView(spinner)
    }

    /**
     * Setup price filter UI and listeners
     */
    private fun setupPriceFilter() {
        // SeekBar listener for price filtering
        seekBarPrice.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val maxPrice = progressToPrice(progress)
                updatePriceLabel(maxPrice)

                // Filter models and update UI
                if (allModels.isNotEmpty()) {
                    filteredModels = modelFetcher.filterByPrice(allModels, maxPrice)
                    updateModelCount()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Refresh button listener
        btnRefreshModels.setOnClickListener {
            fetchModelsFromApi(forceRefresh = true)
        }
    }

    /**
     * Convert seekbar progress to price value
     * Progress 0-100 mapped to price ranges
     */
    private fun progressToPrice(progress: Int): Double {
        return when {
            progress == 0 -> 0.0           // Free only
            progress < 25 -> 0.000001      // Near-free ($0.000001)
            progress < 50 -> 0.0001        // Very cheap ($0.0001)
            progress < 75 -> 0.001         // Cheap ($0.001)
            else -> 0.01                   // Standard ($0.01)
        }
    }

    /**
     * Update price label text
     */
    private fun updatePriceLabel(maxPrice: Double) {
        tvPriceLabel.text = when {
            maxPrice == 0.0 -> "Free models only"
            maxPrice < 0.00001 -> "Up to ~Free"
            else -> "Up to $${"%.6f".format(maxPrice)} per 1K tokens"
        }
    }

    /**
     * Update model count text
     */
    private fun updateModelCount() {
        tvModelCount.text = "${filteredModels.size} models available"
    }

    /**
     * Fetch models from OpenRouter API
     */
    private fun fetchModelsFromApi(forceRefresh: Boolean = false) {
        // Only fetch for OpenRouterLLMRunner
        if (!isOpenRouterRunner()) {
            return
        }

        // Get API key from current settings
        val apiKey = getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            tvModelCount.text = "API key required"
            Log.w(TAG, "Cannot fetch models: API key not set")
            return
        }

        // Show loading state
        tvModelCount.text = "Loading models..."
        btnRefreshModels.isEnabled = false

        lifecycleScope.launch {
            val result = modelFetcher.fetchModels(apiKey, forceRefresh)

            result.onSuccess { models ->
                allModels = models
                val maxPrice = progressToPrice(seekBarPrice.progress)
                filteredModels = modelFetcher.filterByPrice(models, maxPrice)
                updateModelCount()
                btnRefreshModels.isEnabled = true
                Log.d(TAG, "Successfully loaded ${models.size} models from OpenRouter")
            }.onFailure { error ->
                tvModelCount.text = "Failed to load models"
                btnRefreshModels.isEnabled = true
                Log.e(TAG, "Failed to fetch models", error)
                Toast.makeText(
                    this@EngineSettingsActivity,
                    "Failed to fetch models: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Check if current runner is OpenRouterLLMRunner
     */
    private fun isOpenRouterRunner(): Boolean {
        val selectedRunner = getSelectedRunnerName()
        return selectedRunner?.contains("OpenRouter", ignoreCase = true) == true
    }

    /**
     * Get API key from current runner parameters
     */
    private fun getCurrentApiKey(): String? {
        val selectedRunner = getSelectedRunnerName() ?: return null
        val runnerParams = currentSettings.getRunnerParameters(selectedRunner)
        return runnerParams["api_key"] as? String
    }

    /**
     * Show/hide price filter based on runner selection
     */
    private fun updatePriceFilterVisibility() {
        cardPriceFilter.visibility = if (isOpenRouterRunner()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        // Fetch models when OpenRouter runner is selected
        if (isOpenRouterRunner() && allModels.isEmpty()) {
            fetchModelsFromApi()
        }
    }
}