package com.mtkresearch.breezeapp.edgeai.integration.helpers

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Data Loader for Data-Driven Testing.
 * Reads JSON files from assets/test_data and parses them into strongly typed objects.
 */
object TestDataLoader {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().context // Use test context for assets

    // --- Category 1: Compliance ---
    fun loadCategory1Data(): Category1Data {
        val json = JSONObject(loadJsonFromAssets("test_data/category1_compliance.json"))
        return Category1Data(
            schemaValidationTests = parseSchemaValidationTests(json.getJSONArray("test_1_1_schema_validation")),
            draftSchemaTests = parseDraftSchemaTests(json.getJSONArray("test_1_2_draft_schema")),
            completenessTests = parseCompletenessTests(json.getJSONArray("test_1_3_completeness"))
        )
    }

    // --- Category 2: Behavior ---
    fun loadCategory2Data(): Category2Data {
        val json = JSONObject(loadJsonFromAssets("test_data/category2_behavior.json"))
        return Category2Data(
            classificationTests = parseClassificationTests(json.getJSONArray("test_2_1_classification")),
            translationTests = parseTranslationTests(json.getJSONArray("test_2_2_translation")),
            draftQualityTests = parseDraftQualityTests(json.getJSONArray("test_2_3_draft_quality"))
        )
    }

    // --- Category 4: Context ---
    fun loadCategory4Data(): Category4Data {
        val json = JSONObject(loadJsonFromAssets("test_data/category4_context.json"))
        return Category4Data(
            retentionTests = parseRetentionTests(json.getJSONArray("test_4_1_retention")),
            longContextTest = parseLongContextTest(json.getJSONObject("test_4_2_long_context"))
        )
    }

    // --- Category 5: Integration ---
    fun loadCategory5Data(): Category5Data {
        val json = JSONObject(loadJsonFromAssets("test_data/category5_integration.json"))
        return Category5Data(
            workflowSimulation = parseWorkflowTests(json.getJSONArray("test_5_1_workflow")),
            latencyTests = parseLatencyTests(json.getJSONArray("test_5_2_latency")),
            compatibilityTests = parseCompatibilityTests(json.getJSONArray("test_5_3_compatibility"))
        )
    }

    // --- Helper Methods ---

    private fun loadJsonFromAssets(fileName: String): String {
        return context.assets.open(fileName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        }
    }

    // Parsing Logic
    
    // ... [Category 1-2 parsing methods unchanged] ...
    private fun parseSchemaValidationTests(array: JSONArray): List<SchemaTest> {
        val list = mutableListOf<SchemaTest>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val expectedFields = mutableListOf<String>()
            if (obj.has("expected_fields")) {
                val arr = obj.getJSONArray("expected_fields")
                for (k in 0 until arr.length()) expectedFields.add(arr.getString(k))
            }
            val expectedValues = mutableMapOf<String, String>()
            if (obj.has("expected_values")) {
                val mapObj = obj.getJSONObject("expected_values")
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    expectedValues[key] = mapObj.getString(key)
                }
            }
            list.add(SchemaTest(obj.getString("input"), if(expectedFields.isNotEmpty()) expectedFields else null, if(expectedValues.isNotEmpty()) expectedValues else null))
        }
        return list
    }

    private fun parseDraftSchemaTests(array: JSONArray): List<DraftSchemaTest> {
        val list = mutableListOf<DraftSchemaTest>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(DraftSchemaTest(
                input = obj.getString("input"),
                expectedRecipient = obj.getJSONObject("expected_values").getString("recipient"),
                checkNotEmpty = obj.optBoolean("expected_draft_content_not_empty", false)
            ))
        }
        return list
    }

    private fun parseCompletenessTests(array: JSONArray): List<CompletenessTest> {
        val list = mutableListOf<CompletenessTest>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(CompletenessTest(
                input = obj.getString("input"),
                expectedType = obj.getString("expected_type"),
                checkCompleteness = obj.optBoolean("check_completeness", true)
            ))
        }
        return list
    }

    private fun parseClassificationTests(array: JSONArray): List<ClassificationTest> {
        val list = mutableListOf<ClassificationTest>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(ClassificationTest(obj.getString("input"), obj.getString("expected_type")))
        }
        return list
    }

    private fun parseTranslationTests(array: JSONArray): List<TranslationTest> {
        val list = mutableListOf<TranslationTest>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val expectedArray = obj.getJSONArray("expected_contains")
            val expectedList = mutableListOf<String>()
            for (j in 0 until expectedArray.length()) expectedList.add(expectedArray.getString(j))
            list.add(TranslationTest(obj.getString("input"), expectedList))
        }
        return list
    }

    private fun parseDraftQualityTests(array: JSONArray): List<DraftQualityTest> {
        val list = mutableListOf<DraftQualityTest>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val checksArray = obj.getJSONArray("checks")
            val checks = mutableListOf<DraftCheck>()
            for (j in 0 until checksArray.length()) {
                val c = checksArray.getJSONObject(j)
                checks.add(DraftCheck(
                    type = c.getString("type"),
                    value = if (c.has("value")) c.getString("value") else null,
                    min = if (c.has("min")) c.getInt("min") else null,
                    max = if (c.has("max")) c.getInt("max") else null
                ))
            }
            list.add(DraftQualityTest(input = obj.getString("input"), checks = checks))
        }
        return list
    }
    
    // ... [Category 4 methods unchanged] ...
    private fun parseRetentionTests(array: JSONArray): List<RetentionScenario> {
        val list = mutableListOf<RetentionScenario>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val turnsArray = obj.getJSONArray("turns")
            val turns = mutableListOf<RetentionTurn>()
            for (j in 0 until turnsArray.length()) {
                val t = turnsArray.getJSONObject(j)
                val expects = mutableListOf<String>()
                if (t.has("expect_contains")) expects.add(t.getString("expect_contains"))
                if (t.has("expect_contains_any")) {
                    val arr = t.getJSONArray("expect_contains_any")
                    for (k in 0 until arr.length()) expects.add(arr.getString(k))
                }
                turns.add(RetentionTurn(input = t.getString("input"), expectedContainsAny = expects))
            }
            list.add(RetentionScenario(id = obj.getString("id"), turns = turns))
        }
        return list
    }

    private fun parseLongContextTest(obj: JSONObject): LongContextTest {
        return LongContextTest(
            fillerPrompt = obj.optString("filler_prompt", "Generate context"),
            targetTokenCount = obj.optInt("target_token_count", 8000),
            finalQuestion = obj.getString("final_question"),
            expectedAnswerContains = obj.optString("expected_answer_contains", "")
        )
    }

    // ... [Category 5 Workflow and Compatibility unchanged] ...
    private fun parseWorkflowTests(array: JSONArray): List<WorkflowStep> {
        val list = mutableListOf<WorkflowStep>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(WorkflowStep(
                stepName = obj.getString("step_name"),
                input = obj.getString("input"),
                expectedType = obj.getString("expected_type"),
                expectedRecipient = if (obj.has("expected_recipient")) obj.getString("expected_recipient") else null,
                checkCompleteness = obj.optBoolean("check_completeness", true)
            ))
        }
        return list
    }
    
    private fun parseCompatibilityTests(array: JSONArray): List<CompatibilityTest> {
        val list = mutableListOf<CompatibilityTest>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(CompatibilityTest(
                input = obj.getString("input"),
                expectValidSignalParsing = obj.getBoolean("expect_valid_signal_parsing"),
                expectedParsedType = obj.optString("expected_parsed_type", null)
            ))
        }
        return list
    }

    // UPDATED: Latency Tests to support history
    private fun parseLatencyTests(array: JSONArray): List<LatencyScenario> {
        val list = mutableListOf<LatencyScenario>()
        for (i in 0 until array.length()) {
            val item = array.get(i)
            if (item is String) {
                 list.add(LatencyScenario(input = item))
            } else if (item is JSONObject) {
                 val input = item.getString("input")
                 val useHistory = item.optBoolean("use_history", false)
                 val historyMessages = mutableListOf<SignalHistoryMessage>()
                 
                 if (item.has("history_messages")) {
                     val histArr = item.getJSONArray("history_messages")
                     for (j in 0 until histArr.length()) {
                         val histObj = histArr.getJSONObject(j)
                         historyMessages.add(SignalHistoryMessage(
                             threadId = histObj.getInt("thread_id"),
                             sender = histObj.getString("sender"),
                             body = histObj.getString("body"),
                             quote = if(histObj.has("quote") && !histObj.isNull("quote")) histObj.getString("quote") else null
                         ))
                     }
                 }
                 
                 list.add(LatencyScenario(
                     input = input,
                     useHistory = useHistory,
                     historyMessages = if(historyMessages.isNotEmpty()) historyMessages else null
                 ))
            }
        }
        return list
    }
}

// --- Data Classes ---

// [Prior Categories unchanged]
data class Category1Data(
    val schemaValidationTests: List<SchemaTest>,
    val draftSchemaTests: List<DraftSchemaTest>,
    val completenessTests: List<CompletenessTest>
)
data class SchemaTest(val input: String, val expectedFields: List<String>?, val expectedValues: Map<String, String>?)
data class DraftSchemaTest(val input: String, val expectedRecipient: String, val checkNotEmpty: Boolean)
data class CompletenessTest(val input: String, val expectedType: String, val checkCompleteness: Boolean)

data class Category2Data(
    val classificationTests: List<ClassificationTest>,
    val translationTests: List<TranslationTest>,
    val draftQualityTests: List<DraftQualityTest>
)
data class ClassificationTest(val input: String, val expectedType: String)
data class TranslationTest(val input: String, val expectedContains: List<String>)
data class DraftQualityTest(val input: String, val checks: List<DraftCheck>)
data class DraftCheck(val type: String, val value: String?, val min: Int?, val max: Int?)

data class Category4Data(
    val retentionTests: List<RetentionScenario>,
    val longContextTest: LongContextTest
)
data class RetentionScenario(val id: String, val turns: List<RetentionTurn>)
data class RetentionTurn(val input: String, val expectedContainsAny: List<String>)
data class LongContextTest(val fillerPrompt: String, val targetTokenCount: Int, val finalQuestion: String, val expectedAnswerContains: String)

// UPDATED: Category 5 Data
data class Category5Data(
    val workflowSimulation: List<WorkflowStep>,
    val latencyTests: List<LatencyScenario>,
    val compatibilityTests: List<CompatibilityTest>
)
data class WorkflowStep(val stepName: String, val input: String, val expectedType: String, val expectedRecipient: String?, val checkCompleteness: Boolean)

// Updated LatencyScenario to hold Signal-specific history
data class LatencyScenario(
    val input: String, 
    val useHistory: Boolean = false,
    val historyMessages: List<SignalHistoryMessage>? = null
)

data class SignalHistoryMessage(
    val threadId: Int,
    val sender: String,
    val body: String,
    val quote: String?
)

data class CompatibilityTest(val input: String, val expectValidSignalParsing: Boolean, val expectedParsedType: String?)
