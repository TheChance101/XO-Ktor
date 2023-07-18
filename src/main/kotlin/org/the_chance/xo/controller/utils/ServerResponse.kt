package org.the_chance.xo.controller.utils

@kotlinx.serialization.Serializable
data class ServerResponse<T>(
        val value: T?,
        val code: Int?

) {

    companion object {
        fun <T> success(result: T, code: Int = 2): ServerResponse<T> {
            return ServerResponse(
                    value = result,
                    code = code
            )
        }

    }

}
