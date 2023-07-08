package com.grupo3.projectfindpet.modelsRoom

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "user_table")
@Parcelize
data class UserRoom(
    @PrimaryKey
    var userID: String,
    var userName: String,
    var userEmail: String,
    var lostPets: List<PetRoom>,
    var pets: List<PetRoom>,
    var dateCreated: String,
) : Parcelable