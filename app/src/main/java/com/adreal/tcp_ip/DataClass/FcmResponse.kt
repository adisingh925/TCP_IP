package com.adreal.tcp_ip.DataClass

data class FcmResponse(
    val canonical_ids: Int,
    val failure: Int,
    val multicast_id: Long,
    val results: List<Result>,
    val success: Int
)