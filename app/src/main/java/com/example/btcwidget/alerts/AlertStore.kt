package com.example.btcwidget.alerts

import android.content.Context
import com.example.btcwidget.PriceData

/** Persists alert rules and the last evaluated state in SharedPreferences. */
object AlertStore {
    private const val PREFS = "alert_prefs"
    private const val KEY_WAS_MET = "was_met"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<AlertRule> {
        val p = prefs(context)
        return AlertKind.entries.map { kind ->
            AlertRule(
                kind = kind,
                threshold = p.getString("${kind.name}_threshold", null)?.toDoubleOrNull() ?: 0.0,
                currency = p.getString("${kind.name}_currency", "USD") ?: "USD",
                enabled = p.getBoolean("${kind.name}_enabled", false)
            )
        }
    }

    fun anyEnabled(context: Context): Boolean = load(context).any { it.enabled && it.threshold > 0 }

    /**
     * Saves [rule]. Its "already met" state is set from [current] data, so a rule that is
     * already true when saved (e.g. "above" a price we are already above) waits for the
     * next crossing instead of notifying straight away.
     */
    fun save(context: Context, rule: AlertRule, current: PriceData?) {
        val met = loadWasMet(context).toMutableSet()
        met.remove(rule.kind)
        if (current != null && rule.enabled && PriceAlerts.isMet(rule, current)) met += rule.kind
        prefs(context).edit()
            .putString("${rule.kind.name}_threshold", rule.threshold.toString())
            .putString("${rule.kind.name}_currency", rule.currency)
            .putBoolean("${rule.kind.name}_enabled", rule.enabled)
            .putStringSet(KEY_WAS_MET, met.map { it.name }.toSet())
            .apply()
    }

    fun loadWasMet(context: Context): Set<AlertKind> =
        prefs(context).getStringSet(KEY_WAS_MET, emptySet()).orEmpty()
            .mapNotNull { name -> AlertKind.entries.firstOrNull { it.name == name } }
            .toSet()

    fun saveWasMet(context: Context, met: Set<AlertKind>) {
        prefs(context).edit().putStringSet(KEY_WAS_MET, met.map { it.name }.toSet()).apply()
    }
}
