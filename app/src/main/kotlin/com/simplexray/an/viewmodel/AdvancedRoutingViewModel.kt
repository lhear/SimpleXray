package com.simplexray.an.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplexray.an.prefs.Preferences
import com.simplexray.an.protocol.routing.AdvancedRouter
import com.simplexray.an.protocol.routing.AdvancedRouter.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Advanced Routing screen
 */
class AdvancedRoutingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: Preferences = Preferences(application)
    private val gson: Gson = Gson()
    private val routingEngine = RoutingEngine()

    private val _rules = MutableStateFlow<List<RoutingRule>>(emptyList())
    val rules: StateFlow<List<RoutingRule>> = _rules.asStateFlow()

    private val _selectedRule = MutableStateFlow<RoutingRule?>(null)
    val selectedRule: StateFlow<RoutingRule?> = _selectedRule.asStateFlow()

    init {
        loadSavedRules()
    }

    private fun loadSavedRules() {
        viewModelScope.launch {
            val savedRulesJson = prefs.advancedRoutingRules

            if (savedRulesJson != null) {
                try {
                    val type = object : TypeToken<List<RoutingRule>>() {}.type
                    val savedRules: List<RoutingRule> = gson.fromJson(savedRulesJson, type)
                    savedRules.forEach { rule ->
                        routingEngine.addRule(rule)
                    }
                } catch (e: Exception) {
                    // If deserialization fails, load default rules
                    loadDefaultRules()
                }
            } else {
                // No saved rules, load defaults
                loadDefaultRules()
            }

            _rules.value = routingEngine.getAllRules()
        }
    }

    private fun loadDefaultRules() {
        // Add default rule templates
        val defaultRules = listOf(
            RuleTemplates.bypassPrivateIps(),
            RuleTemplates.bypassChinaMainland(),
            RuleTemplates.streamingViaProxy(),
            RuleTemplates.blockAds()
        )

        defaultRules.forEach { rule ->
            routingEngine.addRule(rule)
        }

        saveRules()
    }

    private fun saveRules() {
        try {
            val rulesJson = gson.toJson(_rules.value)
            prefs.advancedRoutingRules = rulesJson
        } catch (e: Exception) {
            // Handle save error
        }
    }

    fun addRule(rule: RoutingRule) {
        viewModelScope.launch {
            routingEngine.addRule(rule)
            _rules.value = routingEngine.getAllRules()
            saveRules()
        }
    }

    fun removeRule(ruleId: String) {
        viewModelScope.launch {
            routingEngine.removeRule(ruleId)
            _rules.value = routingEngine.getAllRules()
            saveRules()
        }
    }

    fun updateRule(rule: RoutingRule) {
        viewModelScope.launch {
            routingEngine.updateRule(rule)
            _rules.value = routingEngine.getAllRules()
            saveRules()
        }
    }

    fun toggleRuleEnabled(ruleId: String) {
        viewModelScope.launch {
            val rule = _rules.value.find { it.id == ruleId }
            if (rule != null) {
                val updatedRule = rule.copy(enabled = !rule.enabled)
                routingEngine.updateRule(updatedRule)
                _rules.value = routingEngine.getAllRules()
                saveRules()
            }
        }
    }

    fun selectRule(rule: RoutingRule?) {
        _selectedRule.value = rule
    }

    fun addTemplateRule(template: RuleTemplate) {
        viewModelScope.launch {
            val rule = when (template) {
                RuleTemplate.BYPASS_CHINA -> RuleTemplates.bypassChinaMainland()
                RuleTemplate.BYPASS_LAN -> RuleTemplates.bypassPrivateIps()
                RuleTemplate.BLOCK_ADS -> RuleTemplates.blockAds()
                RuleTemplate.STREAMING_PROXY -> RuleTemplates.streamingViaProxy()
                RuleTemplate.WORK_HOURS -> RuleTemplates.workHoursOnly()
            }
            routingEngine.addRule(rule)
            _rules.value = routingEngine.getAllRules()
            saveRules()
        }
    }

    fun testRule(rule: RoutingRule, testDomain: String): RoutingAction {
        val context = RoutingContext(
            packageName = null,
            domain = testDomain,
            destinationIp = null,
            destinationPort = null,
            protocol = Protocol.TCP
        )
        return if (rule.matches(context)) rule.action else RoutingAction.Proxy
    }

    enum class RuleTemplate {
        BYPASS_CHINA,
        BYPASS_LAN,
        BLOCK_ADS,
        STREAMING_PROXY,
        WORK_HOURS
    }
}
