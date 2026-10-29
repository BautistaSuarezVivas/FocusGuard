package com.personal.focusguard.data

import com.personal.focusguard.data.db.AppRuleDao
import com.personal.focusguard.data.db.AppRuleEntity
import com.personal.focusguard.domain.model.AppRules
import com.personal.focusguard.domain.model.OpenRule
import com.personal.focusguard.domain.model.OpenWindowType
import com.personal.focusguard.domain.model.UsagePeriodType
import com.personal.focusguard.domain.model.UsageRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RuleRepository(private val dao: AppRuleDao) {

    fun observeRules(): Flow<List<AppRules>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(pkg: String): AppRules? = dao.get(pkg)?.toDomain()

    suspend fun save(rules: AppRules) = dao.upsert(rules.toEntity())

    suspend fun delete(pkg: String) = dao.delete(pkg)

    suspend fun deleteAll() = dao.deleteAll()
}

private fun AppRuleEntity.toDomain() = AppRules(
    packageName = packageName,
    label = label,
    usage = UsageRule(
        enabled = usageEnabled,
        limitMinutes = usageLimitMinutes,
        periodType = enumOr(usagePeriodType, UsagePeriodType.DAILY),
        everyHours = usageEveryHours,
        resetMinuteOfDay = usageResetMinute,
    ),
    opens = OpenRule(
        enabled = openEnabled,
        maxOpens = openMax,
        windowType = enumOr(openWindowType, OpenWindowType.ROLLING),
        windowMinutes = openWindowMinutes,
        anchorMinuteOfDay = openAnchorMinute,
    ),
)

private fun AppRules.toEntity() = AppRuleEntity(
    packageName = packageName,
    label = label,
    usageEnabled = usage.enabled,
    usageLimitMinutes = usage.limitMinutes,
    usagePeriodType = usage.periodType.name,
    usageEveryHours = usage.everyHours,
    usageResetMinute = usage.resetMinuteOfDay,
    openEnabled = opens.enabled,
    openMax = opens.maxOpens,
    openWindowType = opens.windowType.name,
    openWindowMinutes = opens.windowMinutes,
    openAnchorMinute = opens.anchorMinuteOfDay,
)

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback
