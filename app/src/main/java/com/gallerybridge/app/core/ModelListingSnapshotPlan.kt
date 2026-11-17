package com.gallerybridge.app.core

/**
 * Merge already-rendered MHT pages without adding the same numbered page twice.
 * A longer snapshot of a page replaces its shorter snapshot; numbers missing
 * from the visible pagination remain explicit warnings.
 */
internal object ModelListingSnapshotPlan {
    data class Plan(
        val pages: List<ModelListingSources.Page>,
        val result: ModelListingSources.Result
    )

    fun merge(
        previous: List<ModelListingSources.Page>,
        incoming: List<ModelListingSources.Page>
    ): Plan {
        require(incoming.isNotEmpty()) { "Не выбрано ни одного снимка MHT." }
        val all = previous + incoming
        val slug = all.first().address.slug
        require(all.all { it.address.slug == slug }) {
            "Выбраны страницы разных моделей. Импортируйте их отдельно."
        }
        val pages = all.groupBy { it.address.number }
            .values.map { variants ->
                // A browser snapshot with more rendered cards is the safer replacement.
                variants.maxByOrNull { it.cardCount }!!
            }.sortedBy { it.address.number }
        val loaded = pages.mapTo(HashSet()) { it.address.number }
        val linked = pages.flatMap { it.pages.keys }.toSet()
        val missing = (linked - loaded).sorted()
        val errors = missing.map { "Нет снимка страницы №$it." }
        return Plan(pages, ModelListingSources.combine(pages, errors))
    }
}
