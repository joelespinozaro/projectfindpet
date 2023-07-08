package com.grupo3.projectfindpet.modelsRoom

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "message_table")
@Parcelize
@kotlinx.serialization.Serializable
data class MessageRoom(
    @PrimaryKey
    val messageID : String,
    val userCreatorID : String,
    val senderEmail : String,
    val senderName : String,
    val targetEmail : String,
    val targetName : String,
    val messageContent : String,
    val date : String
) : Parcelable