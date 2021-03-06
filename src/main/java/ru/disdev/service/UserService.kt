package ru.disdev.service

import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import ru.disdev.entity.Role
import ru.disdev.entity.User
import ru.disdev.utils.DaemonThreadPool
import ru.disdev.utils.ParseUtils
import ru.disdev.utils.getSafeFileInputStream
import ru.disdev.utils.getSaveFileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.logging.Level
import java.util.logging.Logger
import javax.xml.bind.DatatypeConverter

const val FILE_NAME = "./users.txt"
val PHRASE: ByteArray = DatatypeConverter.parseHexBinary("928ba00830cc1d29a170ed7906c1c3b6")

private val data: ObservableMap<String, User> = FXCollections.observableHashMap()
private var listener: MapChangeListener<String, User>? = null
private val log: Logger = Logger.getLogger("UserService");

fun saveUser(user: User): User {
    data[user.login.value] = user
    writeFile()
    return user
}

fun findByLogin(login: String) = data[login]

fun deleteByLogin(login: String) {
    data.remove(login)
    writeFile()
}

fun findAll(): ObservableList<User> {
    if (listener != null) data.removeListener(listener)
    val list = FXCollections.observableArrayList(data.values)
    listener = MapChangeListener {
        if (it.wasAdded() && it.wasRemoved()) {
            var index: Int = -1
            list.forEachIndexed { i, user ->
                if (user.login.value == it.key) index = i
            }
            if (index >= 0) {
                list[index] = it.valueAdded
            }
        } else if (it.wasAdded()) {
            list.add(it.valueAdded)
        } else if (it.wasRemoved()) {
            list.removeIf { user -> user.login.value == it.key }
        }
    }
    data.addListener(listener)
    return list
}

private fun writeFile() {
    DaemonThreadPool.execute {
        try {
            CSVWriter(OutputStreamWriter(getSaveFileOutputStream(FILE_NAME))).use {
                it.writeAll(data.values.map {
                    val array: Array<String?> = arrayOfNulls(8)
                    array[0] = it.login.value
                    array[1] = it.password.value
                    array[2] = it.firstName.value
                    array[3] = it.lastName.value
                    array[4] = it.blocked.value.toString()
                    array[5] = it.checkPassword.value.toString()
                    array[6] = it.role.value.name
                    array[7] = it.setPassword.value.toString()
                    array
                })
            }
        } catch (e: Exception) {
            log.log(Level.INFO, "Error while saving user", e)
        }

    }
}

internal fun loadData() {
    CSVReader(InputStreamReader(getSafeFileInputStream(FILE_NAME))).use {
        it.readAll().map {
            val user = User()
            user.login.value = it[0]
            user.password.value = it[1]
            user.firstName.value = it[2]
            user.lastName.value = it[3]
            user.blocked.value = ParseUtils.parserBoolean(it[4]).orElse(false)
            user.checkPassword.value = ParseUtils.parserBoolean(it[5]).orElse(true)
            user.role.value = Role.valueOf(it[6])
            user.setPassword.value = ParseUtils.parserBoolean(it[7]).orElse(true)
            user
        }.forEach {
            data[it.login.value] = it
        }
    }
}