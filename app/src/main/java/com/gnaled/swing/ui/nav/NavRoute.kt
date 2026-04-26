package com.gnaled.swing.ui.nav

sealed class NavRoute(val path: String) {
    data object Capture : NavRoute("capture")
    data object Library : NavRoute("library")
    data object Compare : NavRoute("compare")
    data object Settings : NavRoute("settings")

    data object Detail : NavRoute("detail/{id}") {
        const val ARG_ID = "id"
        fun build(id: String) = "detail/$id"
    }
}
