package com.grupo3.projectfindpet.modelsFirebase

class UserFirebase {

    var userID: String = ""
    var userName: String = ""
    var userEmail: String = ""
    var lostPets = HashMap<String, PetFirebase>()
    var pets = HashMap<String, PetFirebase>()
    var dateCreated: String = ""
    var messagesReceived = HashMap<String, MessageFirebase>()
    var messagesSent = HashMap<String, MessageFirebase>()

    constructor()

    constructor(id: String, name: String, email: String,
                lost: HashMap<String, PetFirebase>, pet: HashMap<String, PetFirebase>,
                date: String,
                allMessagesReceived: HashMap<String, MessageFirebase>,
                allMessagesSent: HashMap<String, MessageFirebase>) : this() {
        userID = id
        userName = name
        userEmail = email
        lostPets = lost
        pets = pet
        dateCreated = date
        messagesReceived = allMessagesReceived
        messagesSent = allMessagesSent
    }
}