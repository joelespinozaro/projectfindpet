package com.grupo3.projectfindpet.modelsRoom

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "pet_table")
@Parcelize
@kotlinx.serialization.Serializable
data class PetRoom (
    @PrimaryKey
    var petID : String = "",
    var petName: String? = null,
    var animalType: String? = null, 
    var petBreed: String? = null,
    var petGender: Int = 0,
    var petAge: Int? = null,
    var placeLastSeen: String = "",
    var dateLastSeen: String = "",
    var hour: Int? = null,
    var minute: Int? = null,
    var notes : String? = null,
    var ownerID: String,
    var ownerName: String,
    var ownerEmail: String,
    var isLost : Boolean? = null,
    var isFound : Boolean? = null,
    var petImages : List<String>? = emptyList(),
    var petImagesLocal : List<String>? = emptyList(),
    var petImagesUri: List<String>? = emptyList(),
    var locationLat : Double?,
    var locationLng : Double?,
    var locationAddress : String?
    ) : Parcelable