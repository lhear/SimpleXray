package com.simplexray.an.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplexray.an.protocol.routing.AdvancedRouter
import com.simplexray.an.protocol.routing.AdvancedRouter.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Advanced Routing screen
 */
class AdvancedRoutingViewModel : ViewModel() {

    private val routingEngine = RoutingEngine()

    private val _rules = MutableStateFlow<List<RoutingRule>>(emptyList())
    val rules: StateFlow<List<RoutingRule>> = _rules.asStateFlow()

    private val _selectedRule = MutableStateFlow<RoutingRule?>(null)
    val selectedRule: StateFlow<RoutingRule?> = _selectedRule.asStateFlow()

    init {
        loadDefaultRules()
    }

    private fun loadDefaultRules() {
        viewModelScope.launch {
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

            _rules.value = routingEngine.getAllRules()
        }
    }

    fun addRule(rule: RoutingRule) {
        viewModelScope.launch {
            routingEngine.addRule(rule)
            _rules.value = routingEngine.getAllRules()
        }
    }

    fun removeRule(ruleId: String) {
        viewModelScope.launch {
            routingEngine.removeRule(ruleId)
            _rules.value = routingEngine.getAllRules()
        }
    }

    fun updateRule(rule: RoutingRule) {
        viewModelScope.launch {
            routingEngine.updateRule(rule)
            _rules.value = routingEngine.getAllRules()
        }
    }

    fun toggleRuleEnabled(ruleId: String) {
        viewModelScope.launch {
            val rule = _rules.value.find { it.id == ruleId }
            if (rule != null) {
                val updatedRule = rule.copy(enabled = !rule.enabled)
                routingEngine.updateRule(updatedRule)
                _rules.value = routingEngine.getAllRules()
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
