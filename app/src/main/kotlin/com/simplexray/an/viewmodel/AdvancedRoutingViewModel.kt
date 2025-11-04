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
import com.simplexray.an.protocol.routing.RoutingRepository
import com.simplexray.an.protocol.routing.RouteSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * ViewModel for Advanced Routing screen
 * 
 * UPDATED: Now uses RoutingRepository for state management
 * - Collects route snapshots from SharedFlow
 * - Rules persist across lifecycle events
 * - UI state syncs with routing repository
 */
class AdvancedRoutingViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: Preferences = Preferences(application)
    private val gson: Gson = Gson()
    private val routingEngine = RoutingEngine()

    // Collect route snapshots from RoutingRepository
    private val _routeSnapshot = MutableStateFlow<RouteSnapshot?>(null)
    val routeSnapshot: StateFlow<RouteSnapshot?> = _routeSnapshot.asStateFlow()

    private val _rules = MutableStateFlow<List<RoutingRule>>(emptyList())
    val rules: StateFlow<List<RoutingRule>> = _rules.asStateFlow()

    private val _selectedRule = MutableStateFlow<RoutingRule?>(null)
    val selectedRule: StateFlow<RoutingRule?> = _selectedRule.asStateFlow()

    init {
        // Initialize RoutingRepository
        RoutingRepository.initialize(application)
        
        // Load rules from repository
        loadSavedRules()
        
        // Collect route snapshots from repository
        viewModelScope.launch {
            RoutingRepository.routeSnapshot.collect { snapshot ->
                _routeSnapshot.value = snapshot
                // Update rules from snapshot
                _rules.value = snapshot.routeTable.rules
            }
        }
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
            // RoutingRepository.addRule is called by routingEngine.addRule
        }
    }

    fun removeRule(ruleId: String) {
        viewModelScope.launch {
            routingEngine.removeRule(ruleId)
            _rules.value = routingEngine.getAllRules()
            saveRules()
            // RoutingRepository.removeRule is called by routingEngine.removeRule
        }
    }

    fun updateRule(rule: RoutingRule) {
        viewModelScope.launch {
            routingEngine.updateRule(rule)
            _rules.value = routingEngine.getAllRules()
            saveRules()
            // RoutingRepository.updateRule is called by routingEngine.updateRule
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
