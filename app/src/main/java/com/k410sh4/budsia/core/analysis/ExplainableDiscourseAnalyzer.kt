package com.k410sh4.budsia.core.analysis

import com.k410sh4.budsia.domain.model.AnalysisSignal
import com.k410sh4.budsia.domain.model.SignalType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplainableDiscourseAnalyzer @Inject constructor() {
    private data class Rule(
        val type: SignalType,
        val patterns: List<Regex>,
        val confidence: Float,
        val explanation: String
    )

    private val rules = listOf(
        Rule(
            SignalType.ARTIFICIAL_URGENCY,
            listOf(
                Regex("""\b(agora|imediatamente|última chance|decida hoje|tem que decidir)\b""", RegexOption.IGNORE_CASE),
                Regex("""\b(right now|last chance|decide today|immediately)\b""", RegexOption.IGNORE_CASE)
            ),
            .72f,
            "The wording applies explicit time pressure. That is a persuasion cue, not proof of malicious intent."
        ),
        Rule(
            SignalType.GUILT_PRESSURE,
            listOf(
                Regex("""\b(se você realmente (gostasse|se importasse|fosse meu amigo)|depois de tudo que fiz)\b""", RegexOption.IGNORE_CASE),
                Regex("""\b(if you really (loved|cared)|after everything i did)\b""", RegexOption.IGNORE_CASE)
            ),
            .82f,
            "The request is tied to affection, loyalty or indebtedness, which can create guilt pressure."
        ),
        Rule(
            SignalType.FALSE_DILEMMA,
            listOf(
                Regex("""\b(ou você .+ ou você .+)""", RegexOption.IGNORE_CASE),
                Regex("""\b(either .+ or .+)""", RegexOption.IGNORE_CASE)
            ),
            .64f,
            "The phrasing may present two options as exhaustive. Context is required to decide whether alternatives really exist."
        ),
        Rule(
            SignalType.AD_HOMINEM,
            listOf(
                Regex("""\b(você é (burro|idiota|ignorante)|seu (idiota|burro))\b""", RegexOption.IGNORE_CASE),
                Regex("""\b(you are (stupid|an idiot|ignorant))\b""", RegexOption.IGNORE_CASE)
            ),
            .90f,
            "The statement attacks a person rather than addressing the substance of an argument."
        ),
        Rule(
            SignalType.APPEAL_TO_POPULARITY,
            listOf(
                Regex("""\b(todo mundo (sabe|faz|concorda)|a maioria concorda)\b""", RegexOption.IGNORE_CASE),
                Regex("""\b(everyone knows|everyone does it|most people agree)\b""", RegexOption.IGNORE_CASE)
            ),
            .70f,
            "Popularity is being used as support. Popularity alone does not establish that a claim is true."
        ),
        Rule(
            SignalType.THREAT_OR_COERCION,
            listOf(
                Regex("""\b(se você não .+ (vai se arrepender|vai ver|terá consequências))\b""", RegexOption.IGNORE_CASE),
                Regex("""\b(if you don't .+ (you'll regret|there will be consequences))\b""", RegexOption.IGNORE_CASE)
            ),
            .88f,
            "The wording links non-compliance to a negative consequence. Context is needed to distinguish warning from coercion."
        ),
        Rule(
            SignalType.SCARCITY_PRESSURE,
            listOf(
                Regex("""\b(só resta|últimas unidades|vai acabar|oportunidade única)\b""", RegexOption.IGNORE_CASE),
                Regex("""\b(only .* left|limited stock|will sell out|once-in-a-lifetime)\b""", RegexOption.IGNORE_CASE)
            ),
            .74f,
            "Scarcity language can increase urgency. The app does not verify whether the scarcity claim is true."
        ),
        Rule(
            SignalType.IMPLIED_REQUEST,
            listOf(
                Regex("""\b(seria bom se você|você poderia|não seria melhor se)\b""", RegexOption.IGNORE_CASE),
                Regex("""\b(it would be nice if you|could you|wouldn't it be better if)\b""", RegexOption.IGNORE_CASE)
            ),
            .66f,
            "The phrasing can function as an indirect request rather than a direct factual statement."
        )
    )

    fun analyze(text: String): List<AnalysisSignal> {
        if (text.isBlank()) return emptyList()

        return rules.mapNotNull { rule ->
            val match = rule.patterns.firstNotNullOfOrNull { it.find(text) } ?: return@mapNotNull null
            AnalysisSignal(
                type = rule.type,
                confidence = rule.confidence,
                evidence = match.value,
                explanation = rule.explanation
            )
        }.sortedByDescending { it.confidence }
    }
}
