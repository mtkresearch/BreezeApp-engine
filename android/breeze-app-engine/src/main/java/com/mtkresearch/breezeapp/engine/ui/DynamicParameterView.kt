package com.mtkresearch.breezeapp.engine.ui

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mtkresearch.breezeapp.engine.R
import com.mtkresearch.breezeapp.engine.runner.core.ParameterSchema
import com.mtkresearch.breezeapp.engine.runner.core.ParameterType

/**
 * Dynamic Parameter View
 * 
 * Utility class for generating UI components based on ParameterSchema.
 * This class handles different parameter types and creates appropriate UI controls.
 * 
 * ## Supported Parameter Types
 * - String parameters (regular text, masked for sensitive data)
 * - Integer/Float parameters (sliders with range constraints)
 * - Boolean parameters (switches/toggles)
 * - Selection parameters (dropdowns/spinners)
 */
class DynamicParameterView(private val context: Context) {
    
    /**
     * Create UI views for a list of parameter schemas
     * 
     * @param schemas List of parameter schemas to create views for
     * @param currentValues Current parameter values
     * @param onParameterChange Callback for parameter value changes
     * @return List of created views
     */
    fun createParameterViews(
        schemas: List<ParameterSchema>,
        currentValues: Map<String, Any>,
        onParameterChange: (String, Any) -> Unit
    ): List<View> {
        return schemas.map { schema ->
            createParameterView(schema, currentValues[schema.name], onParameterChange)
        }
    }
    
    /**
     * Create a UI view for a single parameter schema
     * 
     * @param schema Parameter schema to create view for
     * @param currentValue Current value for the parameter
     * @param onParameterChange Callback for parameter value changes
     * @return Created view
     */
    private fun createParameterView(
        schema: ParameterSchema,
        currentValue: Any?,
        onParameterChange: (String, Any) -> Unit
    ): View {
        // Create a container for the parameter view
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        
        // Add parameter label
        val label = TextView(context).apply {
            text = schema.displayName
            textSize = 16f
            setTextColor(context.getColor(android.R.color.black))
        }
        container.addView(label)
        
        // Add parameter description if available
        if (schema.description.isNotEmpty()) {
            val description = TextView(context).apply {
                text = schema.description
                textSize = 12f
                setTextColor(context.getColor(android.R.color.darker_gray))
                setPadding(0, 4, 0, 8)
            }
            container.addView(description)
        }
        
        // Create appropriate UI control based on parameter type
        val control = when (val type = schema.type) {
            is ParameterType.StringType -> {
                createStringInput(schema, type, currentValue, onParameterChange)
            }
            is ParameterType.IntType -> {
                createIntegerInput(schema, type, currentValue, onParameterChange)
            }
            is ParameterType.FloatType -> {
                createFloatInput(schema, type, currentValue, onParameterChange)
            }
            is ParameterType.BooleanType -> {
                createBooleanInput(schema, currentValue, onParameterChange)
            }
            is ParameterType.SelectionType -> {
                createSelectionInput(schema, type, currentValue, onParameterChange)
            }
            is ParameterType.FilePathType -> {
                createFilePathInput(schema, type, currentValue, onParameterChange)
            }
        }
        
        container.addView(control)
        
        // Add divider
        val divider = View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(context.getColor(android.R.color.darker_gray))
            alpha = 0.2f
        }
        container.addView(divider)
        
        return container
    }
    
    /**
     * Create string input control
     */
    private fun createStringInput(
        schema: ParameterSchema,
        type: ParameterType.StringType,
        currentValue: Any?,
        onParameterChange: (String, Any) -> Unit
    ): View {
        val textInputLayout = TextInputLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val editText = TextInputEditText(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            if (schema.isSensitive) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = context.getString(R.string.enter_type_hint, schema.displayName)
            } else {
                inputType = InputType.TYPE_CLASS_TEXT
                hint = context.getString(R.string.enter_type_hint, schema.displayName)
            }
            
            // Set current value
            val textValue = currentValue?.toString() ?: schema.defaultValue?.toString() ?: ""
            setText(textValue)
            
            // Add text change listener
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    onParameterChange(schema.name, s.toString())
                }
                
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        }
        
        textInputLayout.addView(editText)
        return textInputLayout
    }
    
    /**
     * Create integer input control with slider
     */
    private fun createIntegerInput(
        schema: ParameterSchema,
        type: ParameterType.IntType,
        currentValue: Any?,
        onParameterChange: (String, Any) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Create value display
        val valueDisplay = TextView(context).apply {
            text = (currentValue as? Number)?.toInt()?.toString() 
                ?: schema.defaultValue?.toString() 
                ?: "0"
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        container.addView(valueDisplay)
        
        // Create slider
        val slider = Slider(context).apply {
            valueFrom = (type.minValue ?: 0).toFloat()
            valueTo = (type.maxValue ?: 100).toFloat()
            stepSize = (type.step ?: 1).toFloat()
            value = (currentValue as? Number)?.toFloat() 
                ?: (schema.defaultValue as? Number)?.toFloat() 
                ?: valueFrom
            
            addOnChangeListener { _, value, _ ->
                val intValue = value.toInt()
                valueDisplay.text = intValue.toString()
                onParameterChange(schema.name, intValue)
            }
        }
        container.addView(slider)
        
        return container
    }
    
    /**
     * Create float input control with slider
     */
    private fun createFloatInput(
        schema: ParameterSchema,
        type: ParameterType.FloatType,
        currentValue: Any?,
        onParameterChange: (String, Any) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Create value display
        val valueDisplay = TextView(context).apply {
            text = String.format("%.${type.precision}f", 
                (currentValue as? Number)?.toDouble() 
                    ?: (schema.defaultValue as? Number)?.toDouble() 
                    ?: 0.0)
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        container.addView(valueDisplay)
        
        // Create slider
        val slider = Slider(context).apply {
            valueFrom = (type.minValue ?: 0.0).toFloat()
            valueTo = (type.maxValue ?: 1.0).toFloat()
            stepSize = (type.step ?: 0.1).toFloat()
            value = (currentValue as? Number)?.toFloat() 
                ?: (schema.defaultValue as? Number)?.toFloat() 
                ?: valueFrom
            
            addOnChangeListener { _, value, _ ->
                val formattedValue = String.format("%.${type.precision}f", value.toDouble()).toDouble()
                valueDisplay.text = String.format("%.${type.precision}f", formattedValue)
                onParameterChange(schema.name, formattedValue.toFloat())
            }
        }
        container.addView(slider)
        
        return container
    }
    
    /**
     * Create boolean input control
     */
    private fun createBooleanInput(
        schema: ParameterSchema,
        currentValue: Any?,
        onParameterChange: (String, Any) -> Unit
    ): View {
        return SwitchCompat(context).apply {
            text = schema.displayName
            isChecked = (currentValue as? Boolean) 
                ?: (schema.defaultValue as? Boolean) 
                ?: false
            
            setOnCheckedChangeListener { _, isChecked ->
                onParameterChange(schema.name, isChecked)
            }
        }
    }
    
    /**
     * Create selection input control
     */
    private fun createSelectionInput(
        schema: ParameterSchema,
        type: ParameterType.SelectionType,
        currentValue: Any?,
        onParameterChange: (String, Any) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val spinner = Spinner(context)
        
        // Create adapter with selection options
        val options = type.options.map { it.displayName }.toTypedArray()
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter
        
        // Set default selection
        val currentKey = currentValue?.toString() ?: schema.defaultValue?.toString()
        if (currentKey != null) {
            val defaultOptionIndex = type.options.indexOfFirst { it.key == currentKey }
            if (defaultOptionIndex >= 0) {
                spinner.setSelection(defaultOptionIndex)
            }
        }
        
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKey = type.options[position].key
                onParameterChange(schema.name, selectedKey)
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        container.addView(spinner)
        return container
    }
    
    /**
     * Create file path input control
     */
    private fun createFilePathInput(
        schema: ParameterSchema,
        type: ParameterType.FilePathType,
        currentValue: Any?,
        onParameterChange: (String, Any) -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val textInputLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val editText = TextInputEditText(context).apply {
            hint = context.getString(R.string.select_file_path_hint)
            isEnabled = false
            val textValue = currentValue?.toString() ?: schema.defaultValue?.toString() ?: ""
            setText(textValue)
        }
        textInputLayout.addView(editText)
        container.addView(textInputLayout)
        
        val button = Button(context).apply {
            text = "Browse"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                // In a real implementation, this would open a file picker dialog
                // For now, we'll just show a toast
                Toast.makeText(context, "File picker would open here", Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(button)
        
        return container
    }
}