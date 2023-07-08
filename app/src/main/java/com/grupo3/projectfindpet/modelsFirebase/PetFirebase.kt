package com.grupo3.projectfindpet.modelsFirebase

class PetFirebase {

    var petID : String = ""
    var petName : String? = null
    var animalType : String? = null
    var petBreed : String? = null
    var petGender : Int = 0
    var petAge : Int? = null
    var placeLastSeen : String = ""
    var dateLastSeen: String = ""
    var hour : Int? = null
    var minute : Int? = null
    var notes : String? = null
    var ownerID : String = ""
    var ownerName : String = ""
    var ownerEmail : String = ""
    var petImages = HashMap<String, String>()
    var isLost : Boolean? = null
    var isFound : Boolean? = null
    var locationLatLng = HashMap<String, Double>()
    var locationAddress : String? = ""

    constructor()

    constructor(id: String, name: String?, animal: String?,
                breed: String?, gender: Int, age: Int?,
                place: String, date: String, hr: Int?, min: Int?, note: String?,
                userID: String, userName: String,
                userEmail: String,
                images: HashMap<String, String> = HashMap<String, String>(),
                lost: Boolean?, found: Boolean?,
                latLngPoint: HashMap<String, Double> = HashMap<String, Double>(),
                address: String?) : this() {
        petID = id
        petName = name
        animalType = animal
        petBreed = breed
        petGender = gender
        petAge = age
        placeLastSeen = place
        dateLastSeen = date
        hour = hr
        minute = min
        notes = note
        ownerID = userID
        ownerName = userName
        ownerEmail = userEmail
        isLost = lost
        isFound = found
        petImages = images
        locationLatLng = latLngPoint
        locationAddress = address
    }
}