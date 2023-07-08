package com.grupo3.projectfindpet.modelsFirebase

class MessageFirebase {
    var messageID : String = ""
    var senderEmail : String = ""
    var senderName : String = ""
    var targetEmail : String = ""
    var targetName : String = ""
    var messageContent : String = ""
    var date : String = ""

    constructor()

    constructor(id: String, userEmail: String, userName: String, receiverEmail: String,
                receiverName: String, message: String, currentDate: String) {
        messageID = id
        senderEmail = userEmail
        senderName = userName
        targetEmail = receiverEmail
        targetName = receiverName
        messageContent = message
        date = currentDate
    }
}