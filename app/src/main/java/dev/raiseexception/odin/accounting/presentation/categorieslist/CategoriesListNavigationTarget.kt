package dev.raiseexception.odin.accounting.presentation.categorieslist

sealed interface CategoriesListNavigationTarget {
    data class CategoryDetail(val categoryId: String) : CategoriesListNavigationTarget
}
