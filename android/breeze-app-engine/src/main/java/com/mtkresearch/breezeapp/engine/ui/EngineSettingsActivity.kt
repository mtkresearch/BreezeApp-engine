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
import com.mtkresearch.breezeapp.engine.model.ui.ParameterValidationState
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType
import com.mtkresearch.breezeapp.engine.runner.core.RunnerInfo
import com.mtkresearch.breezeapp.engine.runner.core.ValidationResult
import com.mtkresearch.breezeapp.engine.runner.core.RunnerManager
import com.mtkresearch.breezeapp.engine.runner.core.SelectionOption
import com.mtkresearch.breezeapp.engine.runner.openrouter.models.ModelInfo
import com.mtkresearch.breezeapp.engine.runner.openrouter.models.ModelParametersFetcher
import com.mtkresearch.breezeapp.engine.runner.openrouter.models.OpenRouterModelFetcher
import com.mtkresearch.breezeapp.engine.ui.dialogs.showUnsavedChangesDialog
import com.mtkresearch.breezeapp.engine.ui.dialogs.showCriticalErrorDialog
import com.mtkresearch.breezeapp.engine.ui.dialogs.showErrorDialog
import com.mtkresearch.breezeapp.engine.ui.dialogs.ErrorDialog
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
    private lateinit var cardApiKey: com.google.android.material.card.MaterialCardView
    private lateinit var editApiKey: EditText
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
    // Per-runner isolated working parameters (prevents cross-contamination between runners)
    private var currentRunnerParameters: MutableMap<String, MutableMap<String, Any>> = mutableMapOf()
    private var currentEditingRunner: String? = null  // Track which runner we're currently editing
    private var availableRunners: Map<CapabilityType, List<RunnerInfo>> = emptyMap()
    private var runnerParameters: Map<String, List<ParameterSchema>> = emptyMap()

    // Unsaved changes tracking and validation
    private val unsavedChangesState = UnsavedChangesState()
    private val validationState = ParameterValidationState()
    private var navigationConfirmed = false
    private var isLoadingRunners = false  // Flag to prevent tracking programmatic spinner changes

    // Direct RunnerManager access
    private var runnerManager: RunnerManager? = null

    // Parameter field references for validation display
    private data class ParameterFieldViews(
        val inputView: View,
        val errorTextView: TextView
    )
    private val parameterFieldMap = mutableMapOf<String, ParameterFieldViews>()

    // Model fetching (for OpenRouter LLM)
    private var allModels: List<ModelInfo> = emptyList()
    private var filteredModels: List<ModelInfo> = emptyList()
    private var modelSupportedParameters: Set<String>? = null  // Parameters supported by selected model
    private val modelFetcher by lazy {
        OpenRouterModelFetcher(
            prefs = getSharedPreferences("engine_settings_models", MODE_PRIVATE)
        )
    }
    private val parametersFetcher by lazy {
        ModelParametersFetcher()
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
        supportActionBar?.title = getString(R.string.ai_engine_settings)

        // Set navigation icon tint programmatically
        toolbar.navigationIcon?.setTint(ContextCompat.getColor(this, android.R.color.white))
    }
    
    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        cardApiKey = findViewById(R.id.cardApiKey)
        editApiKey = findViewById(R.id.editApiKey)
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

        // Setup API key field and price filter
        setupApiKeyField()
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
                        showBindingErrorDialog()
                    } else {
                        loadSettings()
                        updateCapabilityUI()
                        loadRunnersForCapability()
                        setupObservers()
                    }
                }, 2000)

                Toast.makeText(this, R.string.starting_engine_service, Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing RunnerManager", e)
            showCriticalErrorDialog(
                title = getString(R.string.initialization_error),
                message = getString(R.string.initialization_error_message, e.message)
            )
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
            showErrorDialog(
                title = getString(R.string.settings_load_error),
                message = getString(R.string.settings_load_error_message, e.message)
            )
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
            showErrorDialog(
                title = getString(R.string.runners_load_error),
                message = getString(R.string.runners_load_error_message, e.message)
            )
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
        isLoadingRunners = true  // Mark as programmatic change

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

        isLoadingRunners = false  // Allow user changes to be tracked
    }
    
    private fun updateCapabilityUI() {
        tvCapabilityLabel.text = getString(R.string.select_runner_for_capability, currentCapability.name)
    }

    /**
     * Get the working parameter map for the current runner being edited.
     * Creates a new map if this runner hasn't been edited yet.
     */
    private fun getCurrentRunnerParams(): MutableMap<String, Any> {
        val runner = currentEditingRunner ?: return mutableMapOf()
        return currentRunnerParameters.getOrPut(runner) { mutableMapOf() }
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
                tvRunnerDescription.text = getString(R.string.runner_info_not_available)
                chipSupported.visibility = View.GONE
                chipNotSupported.visibility = View.VISIBLE
            }

            // Load and display parameters for this runner
            loadRunnerParameters(selectedRunnerName)
        } else {
            tvRunnerDescription.text = getString(R.string.no_runners_available)
            chipSupported.visibility = View.GONE
            chipNotSupported.visibility = View.VISIBLE
            clearParameterViews()
        }
    }
    
    private fun loadRunnerParameters(runnerName: String) {
        // Set current editing runner (enables per-runner parameter isolation)
        currentEditingRunner = runnerName

        val schemas = runnerParameters[runnerName] ?: emptyList()
        Log.d(TAG, "loadRunnerParameters for $runnerName: ${schemas.size} schemas available")

        if (schemas.isEmpty()) {
            tvParametersHint.visibility = View.VISIBLE
            containerParameters.visibility = View.GONE
            clearParameterViews()

            // Even with no parameters, update Save button if runner selection changed
            updateSaveButtonForRunnerChange()
            return
        }

        tvParametersHint.visibility = View.GONE
        containerParameters.visibility = View.VISIBLE

        // IMPORTANT: Clear existing views FIRST, before creating new ones
        clearParameterViews()

        // Get current parameter values for this runner
        // IMPORTANT: Prioritize working memory (per-runner) over saved settings
        // This prevents losing user changes when UI regenerates (e.g., model selection changes)
        val savedValues = currentSettings.getRunnerParameters(runnerName)
        val workingValues = currentRunnerParameters[runnerName] ?: emptyMap()
        Log.d(TAG, "loadRunnerParameters: savedValues=$savedValues")
        Log.d(TAG, "loadRunnerParameters: workingValues for $runnerName=$workingValues")
        val currentValues = savedValues.toMutableMap().apply {
            // Override saved values with any working values for THIS runner only
            putAll(workingValues)
        }.toMap()
        Log.d(TAG, "loadRunnerParameters: merged currentValues=$currentValues")

        // Create parameter views - this populates parameterFieldMap
        val parameterViews = createSimpleParameterViews(schemas, currentValues)
        Log.d(TAG, "Created ${parameterViews.size} parameter views, field map size: ${parameterFieldMap.size}")

        // Add new views to container
        parameterViews.forEach { view ->
            containerParameters.addView(view)
        }

        // CRITICAL: Perform initial validation of all current parameters
        validateAllCurrentParameters(runnerName, schemas)
    }

    /**
     * Validate all current parameter values for a runner (called on initial load)
     */
    private fun validateAllCurrentParameters(runnerName: String, schemas: List<ParameterSchema>) {
        val runnerParams = getCurrentRunnerParams()
        Log.d(TAG, "Performing initial validation for $runnerName with ${runnerParams.size} parameters")

        // Clear any previous validation errors for this runner
        validationState.clearRunner(currentCapability, runnerName)

        // Validate each parameter that has a value
        schemas.forEach { schema ->
            val value = runnerParams[schema.name]

            // Skip validation for "model" parameter when using OpenRouter with dynamic models
            // The schema only has one hardcoded model, but API provides many models dynamically
            if (schema.name == "model" && isOpenRouterRunner() && filteredModels.isNotEmpty()) {
                Log.d(TAG, "Skipping validation for dynamically fetched model: $value")
                return@forEach
            }

            val result = validationState.validateParameter(
                capability = currentCapability,
                runnerName = runnerName,
                parameterName = schema.name,
                value = value,
                schema = schema
            )
            if (!result.isValid) {
                Log.w(TAG, "Initial validation FAILED for ${schema.name}: ${result.errorMessage}")
            }
        }

        // Update Save button state based on initial validation
        updateSaveButtonForRunnerChange()
    }

    /**
     * Update Save button state based on runner selection and parameter validation
     *
     * Enables button if:
     * 1. Runner selection changed (always allow saving runner change), OR
     * 2. Parameters changed AND all are valid
     */
    private fun updateSaveButtonForRunnerChange() {
        val selectedRunner = getSelectedRunnerName() ?: return

        val isValid = validationState.isRunnerValid(currentCapability, selectedRunner)
        val errorCount = validationState.getErrorCount(currentCapability, selectedRunner)
        val hasDirtyChanges = unsavedChangesState.hasAnyUnsavedChanges()

        // Check if runner selection changed (not just parameter changes)
        val runnerSelectionChanged = unsavedChangesState.hasChangesForRunner(currentCapability, "RUNNER_SELECTION")

        Log.d(TAG, "updateSaveButtonForRunnerChange: Valid=$isValid, Errors=$errorCount, Dirty=$hasDirtyChanges, RunnerChanged=$runnerSelectionChanged")

        // Enable button if:
        // 1. Runner selection changed (always allow saving runner change), OR
        // 2. Parameters changed AND all are valid
        val shouldEnable = runnerSelectionChanged || (hasDirtyChanges && isValid)
        updateSaveButtonState(shouldEnable)
    }
    
    private fun clearParameterViews() {
        // Clear UI views and field references only
        // Note: Working parameters are preserved per-runner in currentRunnerParameters map
        containerParameters.removeAllViews()
        parameterFieldMap.clear()

        Log.d(TAG, "clearParameterViews: UI cleared for runner $currentEditingRunner")
    }
    
    private fun createSimpleParameterViews(
        schemas: List<ParameterSchema>,
        currentValues: Map<String, Any>
    ): List<android.view.View> {
        val views = mutableListOf<android.view.View>()

        // Filter out api_key since it has its own dedicated card
        var filteredSchemas = schemas.filter { it.name != "api_key" }

        // For OpenRouter models: Filter to only show parameters the model supports
        if (isOpenRouterRunner() && modelSupportedParameters != null) {
            val supportedSet = modelSupportedParameters!!
            filteredSchemas = filteredSchemas.filter { schema ->
                // Always show model parameter, filter others based on support
                schema.name == "model" || supportedSet.contains(schema.name)
            }
            Log.d(TAG, "Filtered parameters for model: showing ${filteredSchemas.map { it.name }}")
        }

        filteredSchemas.forEach { schema ->
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
            when (schema.type) {
                is ParameterType.FloatType -> {
                    Log.d(TAG, "Creating FloatType field for ${schema.name}")
                    // Get the actual value being displayed (prioritize saved value, fallback to default)
                    val initialValue = currentValues[schema.name] ?: schema.defaultValue

                    // Initialize currentRunnerParameters with initial value (so it's saved even if user doesn't change it)
                    if (initialValue != null) {
                        getCurrentRunnerParams()[schema.name] = initialValue
                    }

                    var isInitializing = true

                    val editText = android.widget.EditText(this).apply {
                        hint = getString(R.string.enter_type_hint, schema.type)
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
                                        getCurrentRunnerParams()[schema.name] = value
                                        onParameterChanged(schema.name, value, initialValue)
                                    } else {
                                        // Store the string value for validation
                                        getCurrentRunnerParams()[schema.name] = s.toString()
                                        onParameterChanged(schema.name, s.toString(), initialValue)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Invalid number input for ${schema.name}")
                                }
                            }
                        })
                    }
                    container.addView(editText)

                    // Add error TextView (initially hidden)
                    val errorTextView = TextView(this).apply {
                        textSize = 12f
                        setTextColor(android.graphics.Color.RED)
                        visibility = View.GONE
                        setPadding(8, 4, 8, 4)
                    }
                    container.addView(errorTextView)

                    // Store references for validation display
                    parameterFieldMap[schema.name] = ParameterFieldViews(editText, errorTextView)
                    Log.d(TAG, "Stored FloatType field in map: ${schema.name}")
                }
                is ParameterType.IntType -> {
                    Log.d(TAG, "Creating IntType field for ${schema.name}")
                    val initialValue = currentValues[schema.name] ?: schema.defaultValue

                    // Initialize currentRunnerParameters with initial value
                    if (initialValue != null) {
                        getCurrentRunnerParams()[schema.name] = initialValue
                    }

                    var isInitializing = true

                    val editText = android.widget.EditText(this).apply {
                        hint = getString(R.string.enter_integer_hint)
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
                                        getCurrentRunnerParams()[schema.name] = value
                                        onParameterChanged(schema.name, value, initialValue)
                                    } else {
                                        // Store the string value for validation
                                        getCurrentRunnerParams()[schema.name] = s.toString()
                                        onParameterChanged(schema.name, s.toString(), initialValue)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Invalid integer input for ${schema.name}")
                                }
                            }
                        })
                    }
                    container.addView(editText)

                    // Add error TextView (initially hidden)
                    val errorTextView = TextView(this).apply {
                        textSize = 12f
                        setTextColor(android.graphics.Color.RED)
                        visibility = View.GONE
                        setPadding(8, 4, 8, 4)
                    }
                    container.addView(errorTextView)

                    // Store references for validation display
                    parameterFieldMap[schema.name] = ParameterFieldViews(editText, errorTextView)
                }
                is ParameterType.BooleanType -> {
                    Log.d(TAG, "Creating BooleanType field for ${schema.name}")
                    val initialValue = currentValues[schema.name] ?: schema.defaultValue

                    // Initialize currentRunnerParameters with initial value
                    if (initialValue != null) {
                        getCurrentRunnerParams()[schema.name] = initialValue
                    }

                    var isInitializing = true

                    val switch = android.widget.Switch(this).apply {
                        text = getString(R.string.enable_parameter, schema.name)
                        isChecked = initialValue as? Boolean ?: false

                        setOnCheckedChangeListener { _, isChecked ->
                            if (isInitializing) {
                                isInitializing = false
                                return@setOnCheckedChangeListener
                            }

                            getCurrentRunnerParams()[schema.name] = isChecked
                            onParameterChanged(schema.name, isChecked, initialValue)
                        }
                    }
                    container.addView(switch)

                    // Add error TextView for consistency (though booleans rarely have validation errors)
                    val errorTextView = TextView(this).apply {
                        textSize = 12f
                        setTextColor(android.graphics.Color.RED)
                        visibility = View.GONE
                        setPadding(8, 4, 8, 4)
                    }
                    container.addView(errorTextView)
                    parameterFieldMap[schema.name] = ParameterFieldViews(switch, errorTextView)
                }
                is ParameterType.SelectionType -> {
                    handleSelectionType(schema, currentValues, container)
                }
                is ParameterType.StringType -> {
                    Log.d(TAG, "Creating StringType field for ${schema.name}")
                    // Explicit handling for StringType (includes API keys, etc.)
                    val initialValue = currentValues[schema.name] ?: schema.defaultValue

                    // Initialize currentRunnerParameters with initial value
                    if (initialValue != null) {
                        getCurrentRunnerParams()[schema.name] = initialValue
                    }

                    val editText = android.widget.EditText(this).apply {
                        // Set input type for sensitive fields (passwords, API keys)
                        if (schema.isSensitive) {
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                       android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        } else {
                            inputType = android.text.InputType.TYPE_CLASS_TEXT
                        }
                        hint = schema.displayName

                        // Add text watcher BEFORE setText to properly track initialization
                        var isInitializing = true
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                if (isInitializing) {
                                    isInitializing = false
                                    return
                                }

                                val value = s.toString()
                                getCurrentRunnerParams()[schema.name] = value
                                onParameterChanged(schema.name, value, initialValue)
                            }
                        })

                        // Set initial value AFTER adding listener
                        setText(initialValue?.toString() ?: "")
                    }
                    container.addView(editText)

                    // Add error TextView (initially hidden)
                    val errorTextView = TextView(this).apply {
                        textSize = 12f
                        setTextColor(android.graphics.Color.RED)
                        visibility = View.GONE
                        setPadding(8, 4, 8, 4)
                    }
                    container.addView(errorTextView)

                    // Store references for validation display
                    parameterFieldMap[schema.name] = ParameterFieldViews(editText, errorTextView)
                    Log.d(TAG, "Stored StringType field in map: ${schema.name}")
                }
                is ParameterType.FilePathType -> {
                    Log.d(TAG, "Creating FilePathType field for ${schema.name}")
                    val initialValue = currentValues[schema.name] ?: schema.defaultValue

                    if (initialValue != null) {
                        getCurrentRunnerParams()[schema.name] = initialValue
                    }

                    var isInitializing = true

                    val editText = android.widget.EditText(this).apply {
                        hint = getString(R.string.enter_file_path_hint)
                        inputType = android.text.InputType.TYPE_CLASS_TEXT

                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                if (isInitializing) {
                                    isInitializing = false
                                    return
                                }

                                val value = s.toString()
                                getCurrentRunnerParams()[schema.name] = value
                                onParameterChanged(schema.name, value, initialValue)
                            }
                        })

                        setText(initialValue?.toString() ?: "")
                    }
                    container.addView(editText)

                    // Add error TextView
                    val errorTextView = TextView(this).apply {
                        textSize = 12f
                        setTextColor(android.graphics.Color.RED)
                        visibility = View.GONE
                        setPadding(8, 4, 8, 4)
                    }
                    container.addView(errorTextView)
                    parameterFieldMap[schema.name] = ParameterFieldViews(editText, errorTextView)
                    Log.d(TAG, "Stored FilePathType field in map: ${schema.name}")
                }
                else -> {
                    // Fallback for truly unknown types (shouldn't happen)
                    Log.w(TAG, "Unknown parameter type for ${schema.name}: ${schema.type}")
                    val label = TextView(this).apply {
                        text = getString(R.string.unsupported_parameter_type, schema.type)
                        setTextColor(android.graphics.Color.RED)
                    }
                    container.addView(label)
                }
            }
            
            views.add(container)
        }
        
        return views
    }
    
    private fun setupEventListeners() {
        spinnerRunners.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Skip tracking if this is a programmatic change (during tab switching or initial load)
                if (isLoadingRunners) {
                    updateRunnerInfo()
                    updatePriceFilterVisibility()
                    return
                }

                // Runner selection changed by user - mark as dirty
                val newRunner = spinnerRunners.selectedItem?.toString()
                val originalRunner = currentSettings.selectedRunners[currentCapability]

                Log.d(TAG, "Runner selection: current='$newRunner', saved='$originalRunner'")

                // Track runner selection change only if different from saved value
                if (newRunner != null && newRunner != originalRunner) {
                    // Mark as dirty using a special runner name for runner selection tracking
                    unsavedChangesState.trackChange(
                        currentCapability,
                        "RUNNER_SELECTION",
                        "selected_runner",
                        originalRunner,
                        newRunner
                    )

                    // Enable back button intercept for unsaved changes
                    onBackPressedCallback.isEnabled = true

                    // DON'T enable Save button yet - let parameter validation decide
                    // (parameters will be loaded and validated in updateRunnerInfo below)
                    Log.d(TAG, "Runner selection change tracked, waiting for parameter validation")
                }

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
                    // Pre-save validation: Validate all parameters comprehensively
                    val schemas = runnerParameters[selectedRunnerName] ?: emptyList()
                    val runnerParams = getCurrentRunnerParams()
                    Log.d(TAG, "Pre-save validation: ${schemas.size} schemas, ${runnerParams.size} parameters")
                    Log.d(TAG, "Parameters to validate: ${runnerParams.keys}")

                    val isValid = validationState.validateRunner(
                        capability = currentCapability,
                        runnerName = selectedRunnerName,
                        parameters = runnerParams,
                        schemas = schemas
                    )

                    val errorCount = validationState.getErrorCount(currentCapability, selectedRunnerName)
                    Log.d(TAG, "Pre-save validation result: Valid=$isValid, Errors=$errorCount")

                    if (!isValid) {
                        // Log each validation error
                        schemas.forEach { schema ->
                            val error = validationState.getError(currentCapability, selectedRunnerName, schema.name)
                            if (error != null) {
                                Log.e(TAG, "Validation error for ${schema.name}: $error")
                            }
                        }

                        runOnUiThread {
                            Toast.makeText(
                                this@EngineSettingsActivity,
                                "Cannot save: $errorCount parameter(s) have invalid values. Check logs for details.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }

                    Log.d(TAG, "Pre-save validation passed, proceeding with save")


                    // Update settings with selected runner
                    currentSettings = currentSettings.withRunnerSelection(currentCapability, selectedRunnerName)

                    // Update settings with runner parameters
                    if (runnerParams.isNotEmpty()) {
                        currentSettings = currentSettings.withRunnerParameters(selectedRunnerName, runnerParams.toMap())
                    }

                    // Save settings using RunnerManager
                    runnerManager?.saveSettings(currentSettings)

                    // Clear this runner's working parameters after successful save
                    currentRunnerParameters.remove(selectedRunnerName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving settings", e)
                showErrorDialog(
                    title = getString(R.string.save_error),
                    message = getString(R.string.save_error_message, e.message)
                )
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

                // CRITICAL: Pre-save validation to prevent saving invalid values
                val allSchemas = runnerParameters[selectedRunner] ?: emptyList()

                // Filter out "model" schema when using OpenRouter with dynamic models
                // Dynamic models from API are valid by definition, static schema validation doesn't apply
                val schemas = if (isOpenRouterRunner() && filteredModels.isNotEmpty()) {
                    allSchemas.filter { it.name != "model" }
                } else {
                    allSchemas
                }
                Log.d(TAG, "saveSettingsAndNavigate: Pre-save validation with ${schemas.size} schemas (filtered ${allSchemas.size - schemas.size})")

                val isValid = validationState.validateRunner(
                    capability = currentCapability,
                    runnerName = selectedRunner,
                    parameters = getCurrentRunnerParams(),
                    schemas = schemas
                )

                val errorCount = validationState.getErrorCount(currentCapability, selectedRunner)
                Log.d(TAG, "saveSettingsAndNavigate: Validation result: Valid=$isValid, Errors=$errorCount")

                if (!isValid) {
                    // Log each validation error
                    schemas.forEach { schema ->
                        val error = validationState.getError(currentCapability, selectedRunner, schema.name)
                        if (error != null) {
                            Log.e(TAG, "saveSettingsAndNavigate: Validation error for ${schema.name}: $error")
                        }
                    }

                    hideSaveProgress()
                    runOnUiThread {
                        Toast.makeText(
                            this@EngineSettingsActivity,
                            "Cannot save: $errorCount parameter(s) have invalid values. Please fix them first.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // Build updated settings with runner selection AND parameters
                var updatedSettings = currentSettings.withRunnerSelection(currentCapability, selectedRunner)
                updatedSettings = updatedSettings.withRunnerParameters(
                    selectedRunner,
                    getCurrentRunnerParams()
                )

                // Save via RunnerManager
                runnerManager?.saveSettings(updatedSettings)

                // Update currentSettings so Refresh button works with new values
                currentSettings = updatedSettings

                // Clear dirty state and navigate
                unsavedChangesState.clearAll()
                hideSaveProgress()
                navigationConfirmed = true
                finish()
            } catch (e: Exception) {
                hideSaveProgress()
                Log.e(TAG, "Failed to save settings", e)
                showErrorDialog(
                    title = getString(R.string.save_failed),
                    message = getString(R.string.save_error_message, e.message)
                )
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

                // CRITICAL: Pre-save validation to prevent saving invalid values
                val allSchemas = runnerParameters[selectedRunner] ?: emptyList()

                // Filter out "model" schema when using OpenRouter with dynamic models
                // Dynamic models from API are valid by definition, static schema validation doesn't apply
                val schemas = if (isOpenRouterRunner() && filteredModels.isNotEmpty()) {
                    allSchemas.filter { it.name != "model" }
                } else {
                    allSchemas
                }
                Log.d(TAG, "saveSettingsWithoutNavigate: Pre-save validation with ${schemas.size} schemas (filtered ${allSchemas.size - schemas.size})")

                val isValid = validationState.validateRunner(
                    capability = currentCapability,
                    runnerName = selectedRunner,
                    parameters = getCurrentRunnerParams(),
                    schemas = schemas
                )

                val errorCount = validationState.getErrorCount(currentCapability, selectedRunner)
                Log.d(TAG, "saveSettingsWithoutNavigate: Validation result: Valid=$isValid, Errors=$errorCount")

                if (!isValid) {
                    // Log each validation error
                    schemas.forEach { schema ->
                        val error = validationState.getError(currentCapability, selectedRunner, schema.name)
                        if (error != null) {
                            Log.e(TAG, "saveSettingsWithoutNavigate: Validation error for ${schema.name}: $error")
                        }
                    }

                    hideSaveProgress()
                    runOnUiThread {
                        Toast.makeText(
                            this@EngineSettingsActivity,
                            "Cannot save: $errorCount parameter(s) have invalid values. Please fix them first.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                // Check if model parameter changed (requires runner reload)
                val oldModel = currentSettings.getRunnerParameters(selectedRunner)["model"] as? String
                val newModel = getCurrentRunnerParams()["model"] as? String
                val modelChanged = oldModel != null && newModel != null && oldModel != newModel

                // Build updated settings with runner selection AND parameters
                var updatedSettings = currentSettings.withRunnerSelection(currentCapability, selectedRunner)
                updatedSettings = updatedSettings.withRunnerParameters(
                    selectedRunner,
                    getCurrentRunnerParams()
                )

                // Save via RunnerManager
                runnerManager?.saveSettings(updatedSettings)

                // Update currentSettings so Refresh button works with new values
                currentSettings = updatedSettings

                // Clear dirty state but DON'T navigate
                unsavedChangesState.clearAll()
                hideSaveProgress()

                // Disable save button since no changes now
                btnSave.isEnabled = false
                onBackPressedCallback.isEnabled = false

                // Show appropriate message based on what changed
                val message = if (modelChanged) {
                    getString(R.string.settings_saved_new_conversation)
                } else {
                    getString(R.string.settings_saved_successfully)
                }
                Toast.makeText(this@EngineSettingsActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                hideSaveProgress()
                Log.e(TAG, "Failed to save settings", e)
                showErrorDialog(
                    title = getString(R.string.save_failed),
                    message = getString(R.string.save_error_message, e.message)
                )
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

        // Validate parameter value
        val selectedRunnerSchemas = runnerParameters[selectedRunner] ?: emptyList()
        Log.d(TAG, "Available schemas for $selectedRunner: ${selectedRunnerSchemas.size} schemas")

        val schema = selectedRunnerSchemas.find { it.name == parameterName }
        if (schema != null) {
            // Skip validation for "model" parameter when using OpenRouter with dynamic models
            if (parameterName == "model" && isOpenRouterRunner() && filteredModels.isNotEmpty()) {
                Log.d(TAG, "Skipping validation for dynamically fetched model: $currentValue")
                // Still need to update UI to clear any previous errors
                updateParameterValidationUI(parameterName, ValidationResult.valid())
            } else {
                val validationResult = validationState.validateParameter(
                    capability = currentCapability,
                    runnerName = selectedRunner,
                    parameterName = parameterName,
                    value = currentValue,
                    schema = schema
                )
                Log.d(TAG, "Validation for $parameterName: isValid=${validationResult.isValid}, error=${validationResult.errorMessage}")

                // Update UI to show validation state
                updateParameterValidationUI(parameterName, validationResult)
            }
        } else {
            Log.w(TAG, "No schema found for parameter: $parameterName (available: ${selectedRunnerSchemas.map { it.name }})")
        }

        // Update UI state based on dirty status AND validation
        val hasDirtyChanges = unsavedChangesState.hasAnyUnsavedChanges()
        val isValid = validationState.isRunnerValid(currentCapability, selectedRunner)
        val errorCount = validationState.getErrorCount(currentCapability, selectedRunner)

        // Check if runner selection changed (not just parameter changes)
        val runnerSelectionChanged = unsavedChangesState.hasChangesForRunner(currentCapability, "RUNNER_SELECTION")

        Log.d(TAG, "Dirty state: $hasDirtyChanges, Valid: $isValid, Errors: $errorCount, RunnerChanged: $runnerSelectionChanged (original=$normalizedOriginal, current=$normalizedCurrent)")

        onBackPressedCallback.isEnabled = hasDirtyChanges

        // Enable Save button if:
        // 1. Runner selection changed (always allow saving runner change), OR
        // 2. Parameters changed AND all are valid
        val shouldEnable = runnerSelectionChanged || (hasDirtyChanges && isValid)
        updateSaveButtonState(shouldEnable)

        Log.d(TAG, "Save button enabled: ${btnSave.isEnabled}, Back callback enabled: ${onBackPressedCallback.isEnabled}")
    }

    /**
     * Update parameter field UI to show validation state
     */
    private fun updateParameterValidationUI(parameterName: String, validationResult: com.mtkresearch.breezeapp.engine.runner.core.ValidationResult) {
        val fieldViews = parameterFieldMap[parameterName]

        if (fieldViews == null) {
            Log.w(TAG, "updateParameterValidationUI: No field views found for $parameterName")
            return
        }

        Log.d(TAG, "updateParameterValidationUI: $parameterName isValid=${validationResult.isValid}, error=${validationResult.errorMessage}")

        if (validationResult.isValid) {
            // Valid - hide error, reset field appearance
            fieldViews.errorTextView.visibility = View.GONE
            if (fieldViews.inputView is EditText) {
                fieldViews.inputView.setTextColor(android.graphics.Color.BLACK)
                fieldViews.inputView.setBackgroundResource(android.R.drawable.edit_text)
            }
        } else {
            // Invalid - show error in red, highlight field
            fieldViews.errorTextView.text = validationResult.errorMessage
            fieldViews.errorTextView.visibility = View.VISIBLE

            if (fieldViews.inputView is EditText) {
                val editText = fieldViews.inputView

                // Make text red and bold
                editText.setTextColor(android.graphics.Color.RED)
                editText.setTypeface(null, android.graphics.Typeface.BOLD)

                // Create aggressive red border background
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#FFEEEE")) // Light red background
                    setStroke(5, android.graphics.Color.RED) // Thicker red border
                    cornerRadius = 8f
                }
                editText.background = drawable
                editText.setPadding(16, 16, 16, 16)

                Log.d(TAG, "updateParameterValidationUI: Applied RED styling to $parameterName")
            }
        }
    }

    /**
     * Update Save button visual state (not just enabled/disabled)
     */
    private fun updateSaveButtonState(shouldEnable: Boolean) {
        btnSave.isEnabled = shouldEnable

        // MaterialButton doesn't show disabled state clearly, so we force visual changes
        if (shouldEnable) {
            // Enabled: full opacity, normal color
            btnSave.alpha = 1.0f
            Log.d(TAG, "Save button set to ENABLED (alpha=1.0, fully visible)")
        } else {
            // Disabled: reduce opacity significantly to show it's not clickable
            btnSave.alpha = 0.3f
            Log.d(TAG, "Save button set to DISABLED (alpha=0.3, heavily faded)")
        }

        // Force UI refresh
        btnSave.invalidate()
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
        val initialKey = initialValue?.toString()

        Log.d(TAG, "handleSelectionType for ${schema.name}: initialValue=$initialValue, initialKey=$initialKey")

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

        Log.d(TAG, "handleSelectionType for ${schema.name}: ${options.size} options available")
        Log.d(TAG, "Options: ${options.map { "${it.displayName} (${it.key})" }}")

        // Initialize currentRunnerParameters with the initial value (so it's saved even if user doesn't change it)
        if (initialKey != null) {
            getCurrentRunnerParams()[schema.name] = initialKey
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

            // IMPORTANT: Add listener BEFORE setting selection
            // This ensures the initialization callback gets skipped, not the user's first click
            var isInitializing = true
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isInitializing) {
                        isInitializing = false
                        Log.d(TAG, "Skipping initialization callback for ${schema.name}")
                        return
                    }

                    val selectedOption = options[position]
                    Log.d(TAG, "User selected ${schema.name}: ${selectedOption.displayName} (key=${selectedOption.key})")
                    getCurrentRunnerParams()[schema.name] = selectedOption.key
                    onParameterChanged(schema.name, selectedOption.key, initialKey)

                    // If this is a model selection change in OpenRouter, fetch supported parameters
                    if (schema.name == "model" && isOpenRouterRunner()) {
                        fetchModelSupportedParameters(selectedOption.key)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // Set initial selection AFTER adding listener
            // This will trigger onItemSelected with isInitializing=true
            val initialIndex = options.indexOfFirst { it.key == initialKey }
            Log.d(TAG, "Setting initial selection for ${schema.name}: key=$initialKey, index=$initialIndex")
            if (initialIndex >= 0) {
                setSelection(initialIndex)
                Log.d(TAG, "Spinner selection set to index $initialIndex (${options[initialIndex].displayName})")
            } else {
                Log.w(TAG, "Could not find index for key=$initialKey in ${options.size} options")
            }
        }

        container.addView(spinner)

        // Add error TextView for consistency (though selections rarely have validation errors)
        val errorTextView = TextView(this).apply {
            textSize = 12f
            setTextColor(android.graphics.Color.RED)
            visibility = View.GONE
            setPadding(8, 4, 8, 4)
        }
        container.addView(errorTextView)
        parameterFieldMap[schema.name] = ParameterFieldViews(spinner, errorTextView)
        Log.d(TAG, "Stored SelectionType field in map: ${schema.name}")
    }

    /**
     * Fetch and apply supported parameters for a specific model
     */
    private fun fetchModelSupportedParameters(modelId: String) {
        val apiKey = getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "Cannot fetch model parameters: no API key")
            return
        }

        Log.d(TAG, "Fetching supported parameters for model: $modelId")

        lifecycleScope.launch {
            val result = parametersFetcher.fetchSupportedParameters(modelId, apiKey)

            result.onSuccess { parameters ->
                modelSupportedParameters = parameters.toSet()
                Log.d(TAG, "Model $modelId supports ${parameters.size} parameters: $parameters")

                // Regenerate parameter views to show only supported ones
                updateRunnerInfo()
            }.onFailure { error ->
                Log.w(TAG, "Failed to fetch model parameters (showing all): ${error.message}")
                // Keep showing all parameters on error
                modelSupportedParameters = null
            }
        }
    }

    /**
     * Setup API key field with change tracking
     */
    private fun setupApiKeyField() {
        var isInitializing = true

        editApiKey.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isInitializing) {
                    isInitializing = false
                    return
                }

                val apiKey = s.toString()
                val selectedRunner = getSelectedRunnerName() ?: return

                // Get original API key for comparison
                val originalApiKey = currentSettings.getRunnerParameters(selectedRunner)["api_key"] as? String ?: ""

                // Track API key change
                getCurrentRunnerParams()["api_key"] = apiKey
                onParameterChanged("api_key", apiKey, originalApiKey)
            }
        })
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
            maxPrice == 0.0 -> getString(R.string.free_models_only)
            maxPrice < 0.00001 -> getString(R.string.price_up_to_free)
            else -> getString(R.string.price_up_to_format, "%.6f".format(maxPrice))
        }
    }

    /**
     * Update model count text
     */
    private fun updateModelCount() {
        tvModelCount.text = getString(R.string.models_available_count, filteredModels.size)
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
            tvModelCount.text = getString(R.string.api_key_required)
            Log.w(TAG, "Cannot fetch models: API key not set")
            return
        }

        // Show loading state
        tvModelCount.text = getString(R.string.loading_models)
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

                // Regenerate parameter views to update model dropdown
                updateRunnerInfo()
            }.onFailure { error ->
                tvModelCount.text = getString(R.string.failed_to_load_models)
                btnRefreshModels.isEnabled = true
                Log.e(TAG, "Failed to fetch models", error)
                showErrorDialog(
                    title = getString(R.string.model_fetch_failed),
                    message = getString(R.string.model_fetch_failed_message, error.message)
                )
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
     * Show/hide API key and price filter based on runner selection
     */
    private fun updatePriceFilterVisibility() {
        val isOpenRouter = isOpenRouterRunner()

        // Show/hide both cards for OpenRouter
        cardApiKey.visibility = if (isOpenRouter) View.VISIBLE else View.GONE
        cardPriceFilter.visibility = if (isOpenRouter) View.VISIBLE else View.GONE

        if (isOpenRouter) {
            // Load API key from settings into the dedicated field
            val selectedRunner = getSelectedRunnerName()
            if (selectedRunner != null) {
                val apiKey = currentSettings.getRunnerParameters(selectedRunner)["api_key"] as? String ?: ""
                editApiKey.setText(apiKey)
                getCurrentRunnerParams()["api_key"] = apiKey
            }

            // Fetch models when OpenRouter runner is selected
            if (allModels.isEmpty()) {
                fetchModelsFromApi()
            }
        }
    }

    /**
     * Show EdgeAI binding error dialog with Retry button
     * This is a special case where retry actually works (re-attempts service binding)
     */
    private fun showBindingErrorDialog() {
        ErrorDialog.showWithAction(
            context = this,
            title = getString(R.string.engine_binding_failed),
            message = getString(R.string.engine_binding_failed_message),
            actionButtonText = getString(R.string.retry),
            onAction = {
                Log.d(TAG, "User requested retry for engine binding")
                // Retry initialization
                initializeRunnerManager()
            },
            onClose = {
                Log.d(TAG, "User cancelled binding retry, closing activity")
                finish()
            }
        )
    }
}