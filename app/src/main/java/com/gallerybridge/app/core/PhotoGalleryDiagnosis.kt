package com.gallerybridge.app.core

import org.jsoup.nodes.Document

/**
 * Local, metadata-only explanation of absent gallery sources.
 * Never exposes page URLs/HTML or promotes a preview into the transfer queue.
 */
object PhotoGalleryDiagnosis {
    data class Report(val controls: Int, val previews: Int, val originals: Int) {
        fun summary(): String = when {
            originals > 0 -> "Найдено $originals ссылок-кандидатов. Содержимое будет проверено при скачивании."
            controls > 0 -> "На странице есть элементы галереи ($controls), но подтверждённые ссылки на оригиналы не найдены."
            previews > 0 -> "Обнаружены обложки и миниатюры ($previews), но ссылок на оригиналы нет. Миниатюры не добавляются в очередь."
            else -> "В полученной странице нет распознанных ссылок на оригинальные фотографии."
        }
    }

    fun inspect(document: Document, pageUrl: String): Report {
        if (ModelListingSources.publicationUrl(pageUrl) == null) return Report(0, 0, 0)
        val content = document.select("main#content, article")
        val controls = content.select(
            "a[data-fancybox], a[data-fancybox-group], a.fancy, a.fancybox, a[rel^=lightbox]"
        ).count { anchor ->
            anchor.parents().none { parent ->
                parent.tagName() in setOf("aside", "nav", "footer") ||
                    parent.classNames().any {
                        it.contains("related", true) || it.contains("sidebar", true) ||
                            it.contains("advert", true)
                    }
            }
        }
        val previews = content.select("img[src], img[data-src], img[data-original]").count { img ->
            img.parents().none { parent ->
                parent.tagName() in setOf("aside", "nav", "footer")
            }
        }
        val originals = PhotoGallerySources.extract(document, pageUrl)?.size ?: 0
        return Report(controls, previews, originals)
    }
}
