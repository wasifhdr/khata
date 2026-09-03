package com.wasif.khata.domain.model

data class Category(
    val id: Long,
    val uuid: String,
    val name: String,
    val colorToken: String,
    val parentId: Long?,
    /** Only Uncategorised. It is the fallback label and cannot be deleted. */
    val isSystem: Boolean = false,
)
