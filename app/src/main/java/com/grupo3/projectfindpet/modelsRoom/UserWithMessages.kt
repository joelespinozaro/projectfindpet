package com.grupo3.projectfindpet.modelsRoom

import androidx.room.Embedded
import androidx.room.Relation


data class UserWithMessages (
    @Embedded
    val user : UserRoom,
    @Relation(
        parentColumn = "userID",
        entityColumn = "userCreatorID",
        entity = MessageRoom::class
    )
    var messages : List<MessageRoom>
)
