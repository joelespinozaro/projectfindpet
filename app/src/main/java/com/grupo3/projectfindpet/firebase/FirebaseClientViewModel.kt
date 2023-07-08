package com.grupo3.projectfindpet.firebase

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.grupo3.projectfindpet.AppState
import com.grupo3.projectfindpet.database.FindPetDB
import com.grupo3.projectfindpet.modelsFirebase.PetFirebase
import com.grupo3.projectfindpet.modelsFirebase.MessageFirebase
import com.grupo3.projectfindpet.modelsFirebase.UserFirebase
import com.grupo3.projectfindpet.modelsRoom.PetRoom
import com.grupo3.projectfindpet.modelsRoom.MessageRoom
import com.grupo3.projectfindpet.modelsRoom.UserRoom
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.net.URI
import java.text.SimpleDateFormat
import java.util.regex.Pattern
import kotlin.collections.HashMap
import kotlinx.coroutines.*
import java.util.*

@OptIn(InternalCoroutinesApi::class)
class FirebaseClientViewModel(application: Application) : AndroidViewModel(application) {

    var auth : FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = Firebase.firestore

    private var _userName = MutableLiveData<String>()
    val userName get() = _userName

    private var _userEmail = MutableLiveData<String>()
    val userEmail get() = _userEmail

    private var _userPassword = MutableLiveData<String>()
    val userPassword get() = _userPassword

    var _currentPassword = MutableLiveData<String>()
    val currentPassword get() = _currentPassword

    private var _userConfirmPassword = MutableLiveData<String>()
    val userConfirmPassword get() = _userConfirmPassword

    private var _nameError = MutableLiveData<String>()
    val nameError get() = _nameError

    private var _emailError = MutableLiveData<String>()
    val emailError get() = _emailError

    private var _passwordError = MutableLiveData<String>()
    val passwordError get() = _passwordError

    private var _newPasswordError = MutableLiveData<String>()
    val newPasswordError get() = _newPasswordError

    private var _confirmPasswordError= MutableLiveData<String>()
    val confirmPasswordError get() = _confirmPasswordError

    private var fieldsValidArray = ArrayList<Int>()

    private var coroutineScope = CoroutineScope(Dispatchers.IO)

    var isCreatingUserAccount = false

    private var localDatabase : FindPetDB

    var _appState = MutableLiveData<AppState>(AppState.NORMAL)
    val appState get() = _appState

    // this is the trigger live data that trigger the fetch of a user object
    var userIDLiveData  = MutableLiveData<String>()
    // whenever the userIDLiveData changed, the currentUserLiveData's transformation
    // will be triggered and retrieve the user from local database
    // now, we can observe this variable to update the UI.
    val currentUserRoomLiveData = Transformations.switchMap(userIDLiveData) { id ->
        Log.i("current user live data", "recuperando usuario")
        retrieveUserRoom(id)
    }

    var currentUserID : String = ""
    var currentUserEmail : String = ""

    var _currentUserFirebaseLiveData = MutableLiveData<UserFirebase>()
    val currentUserFirebaseLiveData get() = _currentUserFirebaseLiveData

    val storageRef = Firebase.storage.reference

    val ONE_MEGABYTE: Long = 1024 * 1024

    private var authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser != null) {
            _appState.postValue(AppState.LOGGED_IN)
            Log.i("auth", "cambiado de estado para iniciar sesión")
            // as soon as we got the auth current user, we use its uid to retrieve the
            // user room in local database
            userIDLiveData.postValue(auth.currentUser!!.uid)
            // we also need to retrieve the user firebase from Firestore
            // to get the most updated user profile,
            // we will save it to local room database too
            coroutineScope.launch {
                var userFirebaseDeferred = coroutineScope.async { retrieveUserFirebase() }
                var userFirebase = userFirebaseDeferred.await()
                userFirebase?.let {
                    _currentUserFirebaseLiveData.postValue(userFirebase!!)
                    // update local database
                    localDatabase = FindPetDB.getInstance(application)
                    localDatabase.findPetDAO.insertUser(convertUserFirebaseToUserRoom(userFirebase))
                    processMessagesReceivedFromFirebase(userFirebase)
                }
                // we also retrieve the lost pets and the found pets from Firestore,
                // save to the local database, the local database changes, the data will
                // be retrieved in pets view model.
                //retrievePetsFromFirebase()
                updatePetsList()
                //deleteAllPets()
            }

        } else {
            _appState.postValue(AppState.LOGGED_OUT)
            Log.i("auth", "cambio de estado para cerrar sesión")
        }
    }

    private val nameValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userName) { name ->
            if (name.isNullOrEmpty()) {
                nameError.value = "El nombre no debe estar vacío."
                value = false
            } else {
                value = true
                nameError.value = ""
            }
        }
    }

    private val emailValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userEmail) { email ->
            if (!email.isNullOrEmpty()) {
                if (!isEmailValid(email)) {
                    emailError.value = "Colocar un email válido."
                    value = false
                } else {
                    value = true
                    emailError.value = ""
                }
            } else {
                value = false
            }
            Log.i("¿el email es válido? ", value.toString())
        }
    }

    private val passwordValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userPassword) { password ->
            if (!password.isNullOrEmpty()) {
                if (isPasswordContainSpace(password)) {
                    passwordError.value = "La contraseña no debe tener espacios."
                    value = false
                } else if (password.count() < 8) {
                    passwordError.value = "La contraseña debe tener al menos 8 caracteres."
                    value = false
                } else if (!isPasswordValid(password)) {
                    passwordError.value = "La contraseña solo puede estar compuesta por letras y números."
                    value = false
                } else {
                    passwordError.value = ""
                    value = true
                }
            } else {
                value = false
            }
            Log.i("¿la contraseña es válida? ", value.toString())
        }
    }

    private val confirmPasswordValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(userConfirmPassword) { confirmPassword ->
            if (!confirmPassword.isNullOrEmpty()) {
                if (!isConfirmPasswordValid(userPassword.value!!, confirmPassword)) {
                    confirmPasswordError.value = "Passwords must be the same."
                    value = false
                } else {
                    confirmPasswordError.value = ""
                    value = true
                }
            } else {
                value = false
            }
            Log.i("¿Confirmación válida? ", value.toString())
        }
    }

    private val currentPasswordValid: LiveData<Boolean> = MediatorLiveData<Boolean>().apply {
        addSource(currentPassword) { password ->
            if (!password.isNullOrEmpty()) {
                if (isPasswordContainSpace(password)) {
                    newPasswordError.value = "La contraseña no debe tener espacios."
                    value = false
                } else if (password.count() < 8) {
                    newPasswordError.value = "La contraseña debe tener al menos 8 caracteres."
                    value = false
                } else if (!isPasswordValid(password)) {
                    newPasswordError.value = "La contraseña solo puede estar compuesta por letras y números."
                    value = false
                } else {
                    newPasswordError.value = ""
                    value = true
                }
            } else {
                value = false
            }
            Log.i("¿contraseña válida? ", value.toString())
        }
    }

    private fun isEmailValid(email: String) : Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isPhoneValid(phone: String) : Boolean {
        return PhoneNumberUtils.isGlobalPhoneNumber(phone)
    }

    private fun isPasswordValid(password: String) : Boolean {
        val passwordPattern = Pattern.compile("^[A-Za-z0-9]{8,20}$")
        return passwordPattern.matcher(password).matches()
    }

    private fun isPasswordContainSpace(password: String) : Boolean {
        return password.contains(" ")
    }

    private fun isConfirmPasswordValid(password: String, confirmPassword: String) : Boolean {
        return password == confirmPassword
    }

    private fun sumFieldsValue() : Boolean {
        return fieldsValidArray.sum() == 4
    }
    var readyRegisterLiveData = MediatorLiveData<Boolean>()
    var readyLoginLiveData = MediatorLiveData<Boolean>()
    var readyChangePasswordLiveData = MediatorLiveData<Boolean>()


    init {
        // estos datos en vivo almacenan 5 valores,
        // representa la validez de todos los campos del formulario de registro
        // si el campo correspondiente es válido, el valor en esta matriz, la posición particular
        // será 1, en caso contrario será 0
        // eso hace que la verificación de la validez de todos los campos sea más eficiente,
        // simplemente sumando los 4 campos para ver si es 4, entonces está listo
        fieldsValidArray = arrayListOf(0,0,0,0)
        auth.addAuthStateListener(authStateListener)
        localDatabase = FindPetDB.getInstance(application)

        currentUserRoomLiveData.observeForever(Observer { user ->
            if (user != null) {
                Log.i("user room live data", "retornó usuario")
                Log.i("user room live data", "email: ${user.userEmail}")
                currentUserID = user.userID
                currentUserEmail = user.userEmail
            } else {
                Log.i("current user live data observed", "retornó NULL")
            }
        })

        appState.observeForever( Observer { state ->
            when (state) {
                AppState.READY_CREATE_USER_AUTH -> {
                    coroutineScope.launch {
                        if (createUserOfAuth()) {
                            _appState.postValue(AppState.READY_CREATE_USER_FIREBASE)
                        } else {
                            _appState.postValue(AppState.ERROR_CREATE_USER_AUTH)
                        }
                    }
                }
                AppState.ERROR_CREATE_USER_AUTH -> {
                    resetAllFields()
                    _appState.value = AppState.NORMAL
                }
                AppState.READY_CREATE_USER_FIREBASE -> {
                    Log.i("ready create user firebase", "ID de usuario de autenticación ${auth.currentUser!!.uid}")
                    // we format the date created here
                    val currentDate = Date()
                    val dateFormat = SimpleDateFormat("yyyy/MM/dd hh:mm:ss")
                    //Calendar.DAY_OF_MONTH
                    val dateString = dateFormat.format(currentDate)
                    val user = createUserFirebase(
                        id = auth.currentUser!!.uid,
                        name = userName.value!!,
                        email = userEmail.value!!,
                        lost = HashMap<String, PetFirebase>(),
                        pets = HashMap<String, PetFirebase>(),
                        dateCreated = dateString)
                    coroutineScope.launch {
                        if (saveUserFirebase(user) && saveEmailFirestore(userEmail.value!!)) {
                            // we also create the user room and save it here
                            val userRoom = convertUserFirebaseToUserRoom(user)

                            Log.i("creating and saving the user room", "userID: ${userRoom.userID}")
                            saveUserRoom(userRoom)
                            _appState.postValue(AppState.SUCCESS_CREATED_USER_ACCOUNT)
                        } else {
                            _appState.postValue(AppState.ERROR_CREATE_USER_ACCOUNT)
                        }

                    }
                }
                AppState.EMAIL_ALREADY_EXISTS -> {
                    userEmail.value = ""
                }
                AppState.EMAIL_SERVER_ERROR -> {
                    resetAllFields()
                }
                AppState.ERROR_CREATE_USER_ACCOUNT -> {
                    resetAllFields()
                }
                AppState.SUCCESS_CREATED_USER_ACCOUNT -> {
                    resetAllFields()
                }
                AppState.LOGGED_IN -> {
                    //resetAllFields()
                    Log.i("firebaseClient", "Inicio de sesión detectado")
                    if (isCreatingUserAccount) {
                        _appState.postValue(AppState.READY_CREATE_USER_FIREBASE)
                    }
                }
                AppState.INCORRECT_CREDENTIALS -> {
                    resetAllFields()
                }
                AppState.RESET -> {
                    resetAllFields()
                    _appState.value = AppState.NORMAL
                }
                else -> 0
            }
        })


        // estos datos en vivo observan todas las validezes de los datos en vivo de los campos
        // establece el valor de fieldsValidArray de acuerdo con la validez.
        // siempre que la suma de los camposValidArray sea 4, estos datos devuelven verdadero
        // más falso
        readyRegisterLiveData.addSource(nameValid) { valid ->
            if (valid) {
                fieldsValidArray[0] = 1
                // check other fields validity
                readyRegisterLiveData.value = sumFieldsValue()
            } else {
                fieldsValidArray[0] = 0
                readyRegisterLiveData.value = false
            }
        }
        readyRegisterLiveData.addSource(emailValid) { valid ->
            if (valid) {
                fieldsValidArray[1] = 1
                // check other fields validity
                readyRegisterLiveData.value = sumFieldsValue()
            } else {
                fieldsValidArray[1] = 0
                readyRegisterLiveData.value = false
            }
        }

        readyRegisterLiveData.addSource(passwordValid) { valid ->
            if (valid) {
                fieldsValidArray[2] = 1
                // check other fields validity
                readyRegisterLiveData.value = sumFieldsValue()
            } else {
                fieldsValidArray[2] = 0
                readyRegisterLiveData.value = false
            }
        }
        readyRegisterLiveData.addSource(confirmPasswordValid) { valid ->
            if (valid) {
                fieldsValidArray[3] = 1
                // check other fields validity
                readyRegisterLiveData.value = sumFieldsValue()
            } else {
                fieldsValidArray[3] = 0
                readyRegisterLiveData.value = false
            }
        }
        readyLoginLiveData.addSource(emailValid) { valid ->
            readyLoginLiveData.value = (valid && passwordValid.value != null && passwordValid.value!!)

        }
        readyLoginLiveData.addSource(passwordValid) { valid ->
            readyLoginLiveData.value = (valid && emailValid.value != null && emailValid.value!!)

        }

        readyChangePasswordLiveData.addSource(passwordValid) { valid ->
            readyChangePasswordLiveData.value = currentPasswordValid.value != null &&
                    confirmPasswordValid.value != null &&
                valid && currentPasswordValid.value!! && confirmPasswordValid.value!!
        }
        readyChangePasswordLiveData.addSource(currentPasswordValid) { valid ->
            readyChangePasswordLiveData.value = valid &&
                    passwordValid.value != null && confirmPasswordValid.value != null &&
                    passwordValid.value!! && confirmPasswordValid.value!!
        }

        readyChangePasswordLiveData.addSource(confirmPasswordValid) { valid ->
            readyChangePasswordLiveData.value = valid &&
                    passwordValid.value != null &&
                    currentPasswordValid.value != null &&
                    passwordValid.value!!
                    && currentPasswordValid.value!!
        }
    }

    suspend fun loginUserOfAuth() : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            auth
                .signInWithEmailAndPassword(userEmail.value!!.lowercase(Locale.getDefault()), userPassword.value!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("firebase auth, sign in", "satisfactorio")
                        cancellableContinuation.resume(true){}
                    } else {
                        Log.i("firebase auth, sign in", "error ${task.exception?.message}")
                        cancellableContinuation.resume(false){}
                    }
                }
        }

    fun logoutUser() {
        Log.i("logout", "cerrando sesión")
        auth.signOut()
        resetAllFields()
    }

    private fun resetAllFields() {
        userName.value = ""
        userEmail.value = ""
        userPassword.value = ""
        userConfirmPassword.value = ""
        currentPassword.value = ""
        nameError.value = ""
        emailError.value = ""
        passwordError.value = ""
        confirmPasswordError.value = ""
        isCreatingUserAccount = false
        currentUserID = ""
        currentUserEmail = ""
    }

    suspend fun checkEmailExistFirestore(newEmail: String) : Int =
        suspendCancellableCoroutine<Int> { cancellableContinuation ->
            firestore
                .collection("emails")
                .whereEqualTo("email", newEmail)
                .get()
                .addOnSuccessListener { docRef ->
                    Log.i("check email exists", "satisfactorio")
                    if (docRef.isEmpty) {
                        // can proceed to registration
                        cancellableContinuation.resume(1) {}
                    } else {
                        // email already exists
                        cancellableContinuation.resume(2) {}
                    }
                }
                .addOnFailureListener { e ->
                    Log.i("check email exists", "error: ${e.message}")
                    cancellableContinuation.resume(0) {}
                }
    }

    // we test if firebase auth already has the email by checking if the email signin exists
    // for the email.
    suspend fun checkEmailExistFirebaseAuth(email: String) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            auth
                .fetchSignInMethodsForEmail(email)
                .addOnCompleteListener { task ->
                    if (task.result.signInMethods?.size == 0) {
                        // email not exist
                        Log.i("check firebase auth email exists", "el email no existe")
                        cancellableContinuation.resume(false) {}
                    } else {
                        // email exists
                        Log.i("check firebase auth email exists", "el email existe")
                        cancellableContinuation.resume(true) {}
                    }
                }
    }

    private suspend fun saveEmailFirestore(email: String) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            var data = java.util.HashMap<String, String>()
            data.put("email", email)

            firestore
                .collection("emails")
                .document(email)
                .set(data)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("save email", "success")
                        cancellableContinuation.resume(true) {}
                    } else {
                        Log.i("save email", "failed")
                        cancellableContinuation.resume(false) {}
                    }
                }
    }

    // when we create user firebase, the messages is for sure empty,
    // so, I can just put an empty list
    private fun createUserFirebase(id: String, name: String, email: String, lost: HashMap<String, PetFirebase>,
                                   pets: HashMap<String, PetFirebase>, dateCreated: String) : UserFirebase {
        return UserFirebase(id = id, name = name, email = email,
            lost = HashMap<String, PetFirebase>(), pet = HashMap<String, PetFirebase>(),
            allMessagesReceived = HashMap<String, MessageFirebase>(),
            allMessagesSent = HashMap<String, MessageFirebase>(),
            date = dateCreated)
    }

    private suspend fun saveUserFirebase(user: UserFirebase) =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            firestore
                .collection("users")
                .document(user.userID)
                .set(user)
                .addOnSuccessListener { docRef ->
                    Log.i("firestore", "registro de nuevo usuario satisfactorio")
                    cancellableContinuation.resume(true){}
                }
                .addOnFailureListener { e ->
                    Log.i("firestore", "error en registro de usuario: ${e.message}")
                    cancellableContinuation.resume(false){}
                }
    }

    private fun convertUserFirebaseToUserRoom(userFirebase: UserFirebase) : UserRoom {
        return UserRoom(userID = userFirebase.userID, userName = userFirebase.userName,
            userEmail = userFirebase.userEmail, dateCreated = userFirebase.dateCreated,
            lostPets = convertPetMapToPetList(userFirebase.lostPets),
            pets = convertPetMapToPetList(userFirebase.pets)
        )
    }

    private fun saveUserRoom(user: UserRoom) {
        coroutineScope.launch {
            localDatabase.findPetDAO.insertUser(user)
        }
    }

    private suspend fun retrieveUserFirebase() : UserFirebase? =
        suspendCancellableCoroutine<UserFirebase?> { cancellableContinuation ->
            firestore
                .collection("users")
                .document(auth.currentUser!!.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (!document.exists()) {
                        Log.i("retrieve user firebase", "no se pudo encontrar el usuario")
                        cancellableContinuation.resume(null) {}
                    } else {
                        Log.i("retrieve user firebase", "se encontró el usuario")
                        val user = document.toObject(UserFirebase::class.java)!!
                        Log.i("retrieve user firebase", user.userName)
                        cancellableContinuation.resume(user) {}
                    }
                }
        }


    private fun retrieveUserRoom(id: String) : LiveData<UserRoom> {
        return localDatabase.findPetDAO.getUser(id)
    }

    private suspend fun retrieveAllPetsFirebase(lostOrFound: Boolean) : List<PetFirebase> =
        suspendCancellableCoroutine<List<PetFirebase>> { cancellableContinuation ->
            var collectionName = ""
            if (lostOrFound) {
                collectionName = "lostPets"
            } else {
                collectionName = "foundPets"
            }
            firestore
                .collection(collectionName)
                .get()
                .addOnSuccessListener { documents ->
                    Log.i("retrieve all pets", "satisfactorio")
                    val petsList = ArrayList<PetFirebase>()
                    if (!documents.isEmpty) {
                        documents.map { doc ->
                            val pet = doc.toObject(PetFirebase::class.java)
                            petsList.add(pet)
                            Log.i("retrieve all pets", "se agregó una mascota ${pet.petName}")
                        }
                    }
                    cancellableContinuation.resume(petsList) {}
                }
                .addOnFailureListener { e ->
                    Log.i("retrieve all pets", "erro: ${e.message}")
                    cancellableContinuation.resume(ArrayList()) {}
                }

    }

    private suspend fun retrievePetsFromFirebase() : List<PetFirebase> {
        var lostPetList = ArrayList<PetFirebase>()
        var foundPetList = ArrayList<PetFirebase>()
        //var petRoomList = ArrayList<PetRoom>()
        return withContext(Dispatchers.IO) {
            lostPetList.addAll(retrieveAllPetsFirebase(true))
            lostPetList.addAll(retrieveAllPetsFirebase(false))
            //cancellableContinuation.resume() {}
            //var result = lostPetList.addAll(foundPetList)
            return@withContext lostPetList
        }
    }

    private fun convertPetMapToPetList(petHashmap: HashMap<String, PetFirebase>) : List<PetRoom> {
        val list = ArrayList<PetRoom>()
        for ((key, value) in petHashmap) {
            list.add(convertPetFirebaseToPetRoom(value))
        }
        return list
    }

    private fun convertMessageMapToMessageList(messageMap: HashMap<String, MessageFirebase>)
        : List<MessageRoom> {
        val list = ArrayList<MessageRoom>()
        for ((key, value) in messageMap) {
            list.add(convertMessageFirebaseToMessageRoom(value))
        }
        return list
    }

    private fun convertMessageFirebaseToMessageRoom(messageFirebase: MessageFirebase) : MessageRoom {
        return MessageRoom(messageID = messageFirebase.messageID,
            senderName = messageFirebase.senderName, senderEmail = messageFirebase.senderEmail,
            messageContent = messageFirebase.messageContent, date = messageFirebase.date,
            targetEmail = messageFirebase.targetEmail, targetName = messageFirebase.targetName,
            userCreatorID = auth.currentUser!!.uid)
    }

    private fun convertPetRoomToPetFirebase(petRoom: PetRoom): PetFirebase {
        return PetFirebase(id = petRoom.petID, name = petRoom.petName, animal = petRoom.animalType,
            gender = petRoom.petGender,
            breed = petRoom.petBreed, age = petRoom.petAge, date = petRoom.dateLastSeen,
            hr = petRoom.hour, min = petRoom.minute, place = petRoom.placeLastSeen,
            userID = petRoom.ownerID, userName = petRoom.ownerName, userEmail = petRoom.ownerEmail,
            lost = petRoom.isLost, found = petRoom.isFound,
            latLngPoint = createLatLngHashmap(petRoom.locationLat, petRoom.locationLng),
            address = petRoom.locationAddress, note = petRoom.notes)
    }

    private fun convertPetFirebaseToPetRoom(petFirebase: PetFirebase): PetRoom {
        return PetRoom(petID = petFirebase.petID, petName = petFirebase.petName,
            animalType = petFirebase.animalType,
            petBreed = petFirebase.petBreed, petGender = petFirebase.petGender,
            petAge = petFirebase.petAge, ownerID = petFirebase.ownerID,
            ownerName = petFirebase.ownerName,
            ownerEmail = petFirebase.ownerEmail, isLost = petFirebase.isLost,
            isFound = petFirebase.isFound, dateLastSeen = petFirebase.dateLastSeen,
            hour = petFirebase.hour, minute = petFirebase.minute,
            placeLastSeen = petFirebase.placeLastSeen,
            locationLat = petFirebase.locationLatLng.get("Lat"),
            locationLng = petFirebase.locationLatLng.get("Lng"),
            locationAddress = petFirebase.locationAddress,
            notes = petFirebase.notes)
    }

    private fun createLatLngHashmap(lat: Double?, lng: Double?) : HashMap<String, Double> {
        val latLngHashmap = HashMap<String, Double>()
        lat?.let {
            latLngHashmap.put("Lat", lat)
        }
        lng?.let {
            latLngHashmap.put("Lng", lng)
        }
        return latLngHashmap
    }

    private fun processMessagesReceivedFromFirebase(user: UserFirebase) {
        // from firebase, we got the user object
        // we convert the user firebase to user room, with the exception of the messages list
        // we separate the message lists and create message room objects and save it
        // by the relations defined in the database classes, the messages will be assigned to
        // the user by the userCreatorID.  It is a one to many relationship
        Log.i("messages received from firebase", "inicio de proceso")

        val allMessagesRoom = ArrayList<MessageRoom>()
        Log.i("messages received from firebase", "mensajes recibidos: ${user.messagesReceived.size}")
        Log.i("messages sent from firebase", "mensajes enviados: ${user.messagesSent.size}")
        allMessagesRoom.addAll(convertMessageMapToMessageList(user.messagesReceived))
        allMessagesRoom.addAll(convertMessageMapToMessageList(user.messagesSent))
        Log.i("messages received from firebase", "todos los mensajes recopilados: ${allMessagesRoom.size}")
        // save to local database
        coroutineScope.launch {
            localDatabase.findPetDAO.insertMessages(*allMessagesRoom.toTypedArray())
            Log.i("messages received from firebase", "mensajes registrados: ${allMessagesRoom.size}")
        }
    }

    private fun updatePetsList() {
        var requestList = ArrayList<PetFirebase>()
        var lostPets = ArrayList<PetFirebase>()
        var petRooms = ArrayList<PetRoom>()
        coroutineScope.launch {
            val lostPetsDeferred = coroutineScope.async {
                retrievePetsFromFirebase() as ArrayList<PetFirebase>
            }
            lostPets = lostPetsDeferred.await()
            Log.i("update pets list from firebase", "mascotas perdidas: ${lostPets.size}")
            val petRoomsDeferred = coroutineScope.async {
                localDatabase.findPetDAO.getAllPets() as ArrayList<PetRoom>
            }
            petRooms = petRoomsDeferred.await()

            requestList =
                getListOfPetsRequestImage(lostPets, petRooms) as ArrayList<PetFirebase>

            // now we can send request to Firestore
            lostPets.map { pet ->
                Log.i("update pets list from firebase", "solicitando 1 mascota: ${pet.petName}")
                // we can start a coroutine here,
                // so, each pet is processed in a seperate coroutine
                // it won't need to wait for the response one by one
                coroutineScope.launch {
                    val petRoom = convertPetFirebaseToPetRoom(pet)
                    if (pet.petImages.values.isNotEmpty()) {
                        val byteArray = requestPetImage(pet)
                        byteArray?.let {
                            // update the corresponding pet room object, with the new image filename
                            // and save the pet object locally
                            val imageUri = saveBitmapInternal(it, pet.petName!!)
                            updatePetRoomWithImageUri(petRoom, imageUri)
                            savePetRoom(petRoom)
                        }
                    } else {
                        savePetRoom(petRoom)
                    }
                }
            }
        }
    }

    private fun retrievePetsFromLocalDatabase() : LiveData<List<PetRoom>> {
        return localDatabase.findPetDAO.getAllLostPets()
    }

    private fun getListOfPetsRequestImage(petListFirestore: ArrayList<PetFirebase>, petListLocal: ArrayList<PetRoom>)
        : List<PetFirebase> {
        var petFirebaseList = ArrayList<PetFirebase>()
        if (!petListFirestore.isNullOrEmpty() && !petListLocal.isNullOrEmpty()) {
            // this new list is already a copy , not references
            petFirebaseList = petListFirestore.toList() as ArrayList
            petListLocal.map { petRoom ->
                val petFirebase = petFirebaseList.find { it.petID == petRoom.petID }
                petFirebase?.let {
                    // that means, there are some images that were not stored in
                    // the local storage yet, so the number is different.
                    if (petRoom.petImagesUri?.size == petFirebase.petImages.size) {
                        petFirebaseList.remove(petFirebase)
                        Log.i("get list of pet image request", "eliminando una mascota")
                    }
                }
            }
        }
        return petFirebaseList
    }

    // we send the request one by one,
    // we need to save the images in a place that the app can access
    // we store the local url in pet object and save to local database
    //
    private suspend fun requestPetImage(pet: PetFirebase) : ByteArray? =
        suspendCancellableCoroutine<ByteArray?> { cancellableContinuation ->
            // create a httpRef
            if (pet.petImages.values.isNotEmpty()) {
                // in this case, we just randomly get one of the photo to display
                val httpsRef = Firebase.storage.getReferenceFromUrl(pet.petImages.values.first())
                httpsRef
                    .getBytes(ONE_MEGABYTE)
                    .addOnSuccessListener { byteArray ->
                        Log.i("request pet image from cloud storage", "satisfactorio")
                        cancellableContinuation.resume(byteArray) {}
                    }
                    .addOnFailureListener { e ->
                        Log.i("request pet image from cloud storage", "error: ${e.message}")
                        cancellableContinuation.resume(null) {}
                    }
            }
    }

    private fun convertByteArrayToBitmap(byteArray: ByteArray) : Bitmap {
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }

    private fun saveBitmapInternal(byteArray: ByteArray, petName: String) : URI {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss")
        val fileName = "$timeStamp$petName.jpg"
        var imageUri : URI? = null
        getApplication<Application>().applicationContext.apply {
            openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(byteArray)
            }
            val file = getFileStreamPath(fileName)
            imageUri = file.toURI()
            Log.i("save bitmap internally", "uri: $imageUri")
        }

        return imageUri!!
    }

    private fun updatePetRoomWithImageUri(pet: PetRoom, imageUri: URI) {
        var arrayList = ArrayList<String>()
        if (!pet.petImagesUri.isNullOrEmpty()) {
            arrayList = pet.petImagesUri as ArrayList<String>
        }
        arrayList.add(imageUri.toString())
        pet.petImagesUri = arrayList

        coroutineScope.launch {
            localDatabase.findPetDAO.insertPets(pet)
        }
    }

    // for debug
    private fun deleteAllPets() {
        coroutineScope.launch {
            val petsDeferred = coroutineScope.async {
                localDatabase.findPetDAO.getAllPets()
            }
            val pets = petsDeferred.await()
            localDatabase.findPetDAO.deletePets(*pets.toTypedArray())
        }
    }

    // for debug
    fun retrievePetImageInternal(pet: PetRoom) : Bitmap? {
        if (!pet.petImagesLocal.isNullOrEmpty()) {
            val byteArray = getApplication<Application>()
                .applicationContext.openFileInput(pet.petImagesLocal!![0])
                .readBytes()
            return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        }
        return null
    }
    // for debug


    private suspend fun createUserOfAuth() : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            auth
                .createUserWithEmailAndPassword(userEmail.value!!, userPassword.value!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("firebase auth", "crear el éxito del usuario.")
                        cancellableContinuation.resume(true){}
                    } else {
                        Log.i("firebase auth", " error en crear de usuario: ${task.exception?.message}")
                        cancellableContinuation.resume(false){}
                    }
                }
    }



    // new pet reported is saved in different folders according the lostOrFound
    // lost is true, pet is saved in lostPets, lost is false, pet is saved in foundPets
    suspend fun processPetReport(petRoom: PetRoom, data: ByteArray? = null) : Boolean =
        // refactor the convert method!!
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
        if (currentUserID != null && currentUserID != "") {
            // I'll see if there is image byte array in petsVM.
            // if the user uploaded a photo, the byte array will be there
            // I'll replace the new byte array in the petImages
            // if there is no byte array in petsVM, I'll just pass the old petsImages array to the updated
            // pet object.

            // first we upload the pet image
            // we do this first because we need the url string that comes back when we save
            // the image in the storage.
            val petFirebase = convertPetRoomToPetFirebase(petRoom)
            var imageUrl : String? = null
            if (data != null) {
                coroutineScope.launch {
                    val imageUriDeferred = coroutineScope.async {
                        uploadImageFirebase(data, petRoom.petID, petRoom.isLost!!)
                    }
                    imageUrl = imageUriDeferred.await()
                    imageUrl?.let {
                        Log.i("handle lost pet report", "imageUrl: $imageUrl")
                        // update the pet firebase
                        petFirebase.petImages.put(petFirebase.petID, it)
                        var petImages = petRoom.petImages
                        var tempImages = ArrayList<String>()
                        if (!petImages.isNullOrEmpty()) {
                            tempImages = petImages as ArrayList<String>
                        } else {
                            tempImages = ArrayList<String>()
                        }
                        tempImages.add(it)
                        petRoom.petImages = tempImages
                        // if there is image uploaded, we wait for the above process done
                        // before we send the pet info
                        savePetFirebase(petFirebase)
                        savePetRoom(petRoom)
                        cancellableContinuation.resume(updateUserLostPetFirebase(petFirebase)) {}

                    }
                }
            } else {
                // if there is no image uploaded, we send the pet info immediately
                coroutineScope.launch {
                    savePetFirebase(petFirebase)
                    savePetRoom(petRoom)
                    cancellableContinuation.resume(updateUserLostPetFirebase(petFirebase)) {}
                }
            }
        }  else {
            Log.i("handle lost pet report", "No se pudo encontrar el usuario actual")
            cancellableContinuation.resume(false) {}
        }
    }

    private suspend fun savePetFirebase(pet: PetFirebase) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            var collectionName = ""
            collectionName = if (pet.isLost!!) {
                "lostPets"
            } else {
                "foundPets"
            }
            firestore
                .collection(collectionName)
                .document(pet.petID.toString())
                .set(pet)
                .addOnSuccessListener { docRef ->
                    Log.i("save pet firebase", "satisfactorio")
                    cancellableContinuation.resume(true){}
                }
                .addOnFailureListener { e ->
                    Log.i("save pet firesbase", "error: ${e.message}")
                    cancellableContinuation.resume(false){}
                }
    }

    private fun savePetRoom(pet: PetRoom) {
        coroutineScope.launch {
            localDatabase.findPetDAO.insertPets(pet)
        }
    }

    private suspend fun uploadImageFirebase(data: ByteArray, petID: String, isLost: Boolean) : String? =
        suspendCancellableCoroutine<String?> { cancellableContinuation ->
            var folderName : String = ""
            folderName = if (isLost) {
                "lostPets"
            } else {
                "foundPets"
            }
            // create a ref for the image
            val imageRef = storageRef.child("$folderName/${petID}.jpg")
            val uploadTask = imageRef.putBytes(data)
            uploadTask
                .addOnSuccessListener { taskSnapshot ->
                    Log.i("upload image", "satisfactorio")
                    var imageUri : String? = null
                    taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                        Log.i("upload image", "url - $uri")
                        imageUri = uri.toString()
                        cancellableContinuation.resume(imageUri) {}
                    }
                }
                .addOnFailureListener { e ->
                    Log.i("upload image", "error")
                    cancellableContinuation.resume(null) {}
                }
        }

    // here we retrieve the most updated
    // user firebase from Firestore, and immediately add the lost pet
    // info in it and send it to Firestore.
    private suspend fun updateUserLostPetFirebase(pet: PetFirebase) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            coroutineScope.launch {
                val userDeferred = coroutineScope.async {
                    retrieveUserFirebase()
                }
                var user = userDeferred.await()
                if (user != null) {
                    user.lostPets.put(pet.petID, pet)
                    // save user here
                    if (saveUserFirebase(user)) {
                        Log.i("update user object for lost pet", "satisfactorio")
                        cancellableContinuation.resume(true) {}
                    } else {
                        Log.i("update user object for lost pet", "fallado, tal vez error del servidor")
                        cancellableContinuation.resume(false) {}
                    }
                } else {
                    Log.i("update lost pet in user object", "No puedo encontrar a la usuario en firebase")
                    cancellableContinuation.resume(false){}
                }
            }
        }

    // we write to the messaging collection to trigger the cloud function to process
    // the delievery of the message.  It needs to write in both the messagesReceived in
    // the target user, and the messagesSent in this user's object in firestore.
    suspend fun sendMessageToFirestoreMessaging(messageRoom: MessageRoom) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            var data = HashMap<String, String>()
            data.put("senderEmail", messageRoom.senderEmail)
            data.put("senderName", messageRoom.senderName)
            data.put("targetEmail", messageRoom.targetEmail)
            data.put("targetName", messageRoom.targetName)
            data.put("messageID", messageRoom.messageID)
            data.put("message", messageRoom.messageContent)
            data.put("date", messageRoom.date)

            firestore
                .collection("messaging")
                .document()
                .set(data)
                .addOnSuccessListener { docRef ->
                    Log.i("send to Messaging", "satisfactorio")
                    cancellableContinuation.resume(true) {}
                }
                .addOnFailureListener { e ->
                    Log.i("send to Messaging", "error: ${e.message}")
                    cancellableContinuation.resume(false) {}
                }
    }

    suspend fun changePasswordFirebaseAuth() : Int =
        suspendCancellableCoroutine<Int> { cancellableContinuation ->
            auth.signInWithEmailAndPassword(auth.currentUser!!.email!!, currentPassword.value!!)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("change password", "primer paso, inicio de sesión exitoso")
                        auth.currentUser!!.updatePassword(userPassword.value!!)
                            .addOnCompleteListener { updateTask ->
                                if (updateTask.isSuccessful) {
                                    Log.i("change password", "contraseña actualizada correctamente")
                                    cancellableContinuation.resume(1) {}
                                } else {
                                    Log.i("change password", "error al actualizar contraseña")
                                    cancellableContinuation.resume(2) {}
                                }
                            }
                    } else {
                        Log.i("change password", "\n" +
                                "primer paso, no se pudo iniciar sesión")
                        cancellableContinuation.resume(0) {}
                    }
                }
        }

    suspend fun generatePasswordResetEmail(email: String) : Int =
        suspendCancellableCoroutine<Int> { cancellableContinuation ->
        if (isEmailValid(email)) {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("password reset email", "éxito")
                        cancellableContinuation.resume(1) {}
                    } else {
                        Log.i("password reset email", "error")
                        cancellableContinuation.resume(2) {}
                    }
                }
        } else if (!isEmailValid(email)) {
            cancellableContinuation.resume(0) {}
        }
    }

    // to delete a report, need to go to either lost or found collection
    // also need to update the user's lost pets array to remove it
    // also need to update local database
    suspend fun processDeleteReport(pet: PetRoom) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            coroutineScope.launch {
                var resultCollectionDeferred = coroutineScope.async { deleteReportInCollection(pet) }
                var resultUpdateUserDeferred = coroutineScope.async { deletePetUserFirebase(pet) }
                var resultInCollection = resultCollectionDeferred.await()
                var resultUpdateUser = resultUpdateUserDeferred.await()
                deletePetLocalDatabase(pet)
                deletePetUserLocal(pet)
                cancellableContinuation.resume(resultInCollection && resultUpdateUser) {}
            }
        }



    private suspend fun deleteReportInCollection(pet: PetRoom) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            var collectionName = ""
            if (pet.isLost!!) {
                collectionName = "lostPets"
            } else {
                collectionName = "foundPets"
            }
            firestore
                .collection(collectionName)
                .document(pet.petID)
                .delete()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i("delete pet from collection", "éxito")
                        cancellableContinuation.resume(true) {}
                    } else {
                        Log.i("delete pet from collection", "fallido")
                        cancellableContinuation.resume(false) {}
                    }
                }
        }

    private suspend fun deletePetUserFirebase(pet: PetRoom) : Boolean =
        suspendCancellableCoroutine<Boolean> { cancellableContinuation ->
            firestore
                .collection("users")
                .document(pet.ownerID)
                .get()
                .addOnSuccessListener { docSnapshot ->
                    if (docSnapshot.exists()) {
                        Log.i("deleted pet from user", "obteniendo objeto de usuario")
                        var user = docSnapshot.toObject(UserFirebase::class.java)!!
                        user.lostPets.remove(pet.petID)
                        // save user in firebase
                        coroutineScope.launch {
                            if (saveUserFirebase(user)) {
                                Log.i("delete pet from user", "actualizando usuario")
                                cancellableContinuation.resume(true) {}
                            } else {
                                Log.i("delete pet from user", "no se pudo guardar usuario")
                                cancellableContinuation.resume(false) {}
                            }
                        }
                        // save user in room
                    } else {
                        Log.i("delete pet from user", "falla al obtener objeto de usuario")
                        cancellableContinuation.resume(false) {}
                    }
                }
        }

    private fun deletePetLocalDatabase(pet: PetRoom) {
        localDatabase.findPetDAO.deletePets(pet)
    }

    private fun deletePetUserLocal(petRoom: PetRoom) {
        val pet = currentUserRoomLiveData.value?.lostPets!!.find { pet -> pet.petID == petRoom.petID }
        var petList = ArrayList<PetRoom>()
        if (pet != null) {
            petList = currentUserRoomLiveData.value!!.lostPets as ArrayList
        }
        petList.remove(pet)
        val user = currentUserRoomLiveData.value!!
        user.lostPets = petList
        // save user
        coroutineScope.launch {
            localDatabase.findPetDAO.insertUser(user)
        }
    }
}

class FirebaseClientViewModelFactory(private val application: Application)
    : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FirebaseClientViewModel::class.java)) {
            return FirebaseClientViewModel(application) as T
        }
        throw IllegalArgumentException("Clase ViewModel desconocido")
    }
}
