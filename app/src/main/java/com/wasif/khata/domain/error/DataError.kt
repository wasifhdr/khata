package com.wasif.khata.domain.error

sealed class DataError : Throwable() {
    data object NotFound : DataError()
    data object Storage : DataError()
    data class Unknown(override val cause: Throwable) : DataError()
}
