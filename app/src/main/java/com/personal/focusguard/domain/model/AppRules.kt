package com.personal.focusguard.domain.model

import com.personal.focusguard.domain.period.PeriodSpec

enum class UsagePeriodType { DAILY, WEEKLY, EVERY_N_HOURS }

enum class OpenWindowType { ROLLING, FIXED_BLOCKS }

/** Límite de tiempo de uso en primer plano. */
data class UsageRule(
    val enabled: Boolean = false,
    val limitMinutes: Int = 30,
    val periodType: UsagePeriodType = UsagePeriodType.DAILY,
    /** Solo para [UsagePeriodType.EVERY_N_HOURS]. */
    val everyHours: Int = 4,
    /** Minuto del día del reinicio; null = usar el valor global predeterminado. */
    val resetMinuteOfDay: Int? = null,
) {
    fun periodSpec(defaultResetMinute: Int): PeriodSpec {
        val reset = resetMinuteOfDay ?: defaultResetMinute
        return when (periodType) {
            UsagePeriodType.DAILY -> PeriodSpec.FixedBlocks(24 * 60, reset)
            UsagePeriodType.WEEKLY -> PeriodSpec.Weekly(reset)
            UsagePeriodType.EVERY_N_HOURS -> PeriodSpec.FixedBlocks(everyHours.coerceIn(1, 24) * 60, reset)
        }
    }

    val limitMs: Long get() = limitMinutes.coerceAtLeast(0) * 60_000L
}

/** Límite de cantidad de aperturas. */
data class OpenRule(
    val enabled: Boolean = false,
    val maxOpens: Int = 3,
    val windowType: OpenWindowType = OpenWindowType.ROLLING,
    val windowMinutes: Int = 60,
    /** Para bloques fijos: hora (minuto del día) desde la que se alinean los bloques. */
    val anchorMinuteOfDay: Int = 0,
) {
    fun periodSpec(): PeriodSpec = when (windowType) {
        OpenWindowType.ROLLING -> PeriodSpec.Rolling(windowMinutes.coerceAtLeast(1))
        OpenWindowType.FIXED_BLOCKS -> PeriodSpec.FixedBlocks(windowMinutes.coerceIn(1, 24 * 60), anchorMinuteOfDay)
    }
}

/** Ficha de una app controlada: ambas reglas son independientes entre sí. */
data class AppRules(
    val packageName: String,
    val label: String,
    val usage: UsageRule = UsageRule(),
    val opens: OpenRule = OpenRule(),
) {
    val isActive: Boolean get() = usage.enabled || opens.enabled
}
