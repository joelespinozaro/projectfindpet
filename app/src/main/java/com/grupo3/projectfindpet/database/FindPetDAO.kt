package com.grupo3.projectfindpet.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.grupo3.projectfindpet.modelsRoom.PetRoom
import com.grupo3.projectfindpet.modelsRoom.MessageRoom
import com.grupo3.projectfindpet.modelsRoom.UserRoom
import com.grupo3.projectfindpet.modelsRoom.UserWithMessages

@Dao
interface FindPetDAO {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: UserRoom)

    @Query("SELECT * FROM user_table WHERE :id == userID LIMIT 1")
    fun getUser(id: String) : LiveData<UserRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPets(vararg pet: PetRoom)

    @Query("SELECT * FROM pet_table WHERE :id == petID LIMIT 1")
    fun getPet(id: String) : LiveData<PetRoom>

    @Query("SELECT * FROM pet_table WHERE isLost == 1")
    fun getAllLostPets() : LiveData<List<PetRoom>>

    @Query("SELECT * FROM pet_table WHERE isLost == 0")
    fun getAllFoundPets() : LiveData<List<PetRoom>>

    @Query("SELECT * FROM pet_table")
    fun getAllPets() : List<PetRoom>

    @Delete
    fun deletePets(vararg pet: PetRoom)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(vararg message: MessageRoom)

    @Query("SELECT * FROM message_table WHERE :email == targetEmail")
    fun getAllMessagesReceived(email: String) : LiveData<List<MessageRoom>>

    @Query("SELECT * FROM message_table WHERE :email == senderEmail")
    fun getAllMessagesSent(email: String) : LiveData<List<MessageRoom>>

    @Transaction
    @Query("SELECT * FROM user_table")
    fun getUsersWithMessages() : List<UserWithMessages>

    @Query("SELECT * FROM user_table WHERE :id == userID")
    fun getUserWithMessages(id: String) : LiveData<UserWithMessages>

    //@Query("DELETE * FROM pet_table WHERE :id == petID")
    //fun deletePetByID(id: String)

}