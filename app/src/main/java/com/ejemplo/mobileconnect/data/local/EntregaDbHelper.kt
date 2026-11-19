package com.ejemplo.mobileconnect.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EntregaDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "mobile_connect.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_ENTREGAS = "entregas"
        private const val COL_ID = "id"
        private const val COL_DESTINATARIO = "destinatario"
        private const val COL_DIRECCION = "direccion"
        private const val COL_TELEFONO = "telefono"
        private const val COL_ESTADO = "estado"
        private const val COL_NOTAS = "notas"
        private const val COL_SINCRONIZADO = "sincronizado"
        private const val COL_FECHA = "fecha_actualizacion"

        @Volatile
        private var INSTANCE: EntregaDbHelper? = null

        fun getInstance(context: Context): EntregaDbHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EntregaDbHelper(context).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_ENTREGAS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DESTINATARIO TEXT NOT NULL,
                $COL_DIRECCION TEXT NOT NULL,
                $COL_TELEFONO TEXT NOT NULL,
                $COL_ESTADO TEXT NOT NULL,
                $COL_NOTAS TEXT NOT NULL DEFAULT '',
                $COL_SINCRONIZADO INTEGER NOT NULL DEFAULT 0,
                $COL_FECHA INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createTableSql)
        insertarDatosIniciales(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ENTREGAS")
        onCreate(db)
    }

    private fun insertarDatosIniciales(db: SQLiteDatabase) {
        val iniciales = listOf(
            Entrega(
                destinatario = "Laura Gómez",
                direccion = "C/ Toledo 24, 2ºB",
                telefono = "600111222",
                estado = Entrega.ESTADO_PENDIENTE,
                notas = "Llamar al telefonillo 2B",
                sincronizado = true
            ),
            Entrega(
                destinatario = "Carlos Ruiz",
                direccion = "Av. del Rey Santo 10, Local",
                telefono = "600333444",
                estado = Entrega.ESTADO_PENDIENTE,
                notas = "Entregar en recepción antes de las 14:00",
                sincronizado = true
            ),
            Entrega(
                destinatario = "María López",
                direccion = "Plaza Mayor 5, 1ºA",
                telefono = "600555666",
                estado = Entrega.ESTADO_PENDIENTE,
                notas = "Paquete frágil",
                sincronizado = false
            )
        )

        iniciales.forEach { entrega ->
            db.insert(TABLE_ENTREGAS, null, entregaToContentValues(entrega))
        }
    }

    fun insertar(entrega: Entrega): Long {
        val db = writableDatabase
        return db.insert(TABLE_ENTREGAS, null, entregaToContentValues(entrega))
    }

    fun actualizar(entrega: Entrega): Int {
        val db = writableDatabase
        return db.update(
            TABLE_ENTREGAS,
            entregaToContentValues(entrega),
            "$COL_ID = ?",
            arrayOf(entrega.id.toString())
        )
    }

    fun marcarComoSincronizada(id: Long): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SINCRONIZADO, 1)
        }
        return db.update(
            TABLE_ENTREGAS,
            values,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun eliminar(id: Long): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_ENTREGAS,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun obtenerTodas(): List<Entrega> {
        val lista = mutableListOf<Entrega>()
        val db = readableDatabase
        db.query(
            TABLE_ENTREGAS,
            null,
            null,
            null,
            null,
            null,
            "$COL_ESTADO DESC, $COL_FECHA DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                lista.add(cursorToEntrega(cursor))
            }
        }
        return lista
    }

    fun obtenerPorId(id: Long): Entrega? {
        val db = readableDatabase
        db.query(
            TABLE_ENTREGAS,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursorToEntrega(cursor) else null
        }
    }

    fun obtenerNoSincronizadas(): List<Entrega> {
        val lista = mutableListOf<Entrega>()
        val db = readableDatabase
        db.query(
            TABLE_ENTREGAS,
            null,
            "$COL_SINCRONIZADO = ?",
            arrayOf("0"),
            null,
            null,
            "$COL_FECHA ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                lista.add(cursorToEntrega(cursor))
            }
        }
        return lista
    }

    private fun entregaToContentValues(entrega: Entrega): ContentValues {
        return ContentValues().apply {
            put(COL_DESTINATARIO, entrega.destinatario)
            put(COL_DIRECCION, entrega.direccion)
            put(COL_TELEFONO, entrega.telefono)
            put(COL_ESTADO, entrega.estado)
            put(COL_NOTAS, entrega.notas)
            put(COL_SINCRONIZADO, if (entrega.sincronizado) 1 else 0)
            put(COL_FECHA, entrega.fechaActualizacion)
        }
    }

    private fun cursorToEntrega(cursor: Cursor): Entrega {
        return Entrega(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            destinatario = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESTINATARIO)),
            direccion = cursor.getString(cursor.getColumnIndexOrThrow(COL_DIRECCION)),
            telefono = cursor.getString(cursor.getColumnIndexOrThrow(COL_TELEFONO)),
            estado = cursor.getString(cursor.getColumnIndexOrThrow(COL_ESTADO)),
            notas = cursor.getString(cursor.getColumnIndexOrThrow(COL_NOTAS)),
            sincronizado = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SINCRONIZADO)) == 1,
            fechaActualizacion = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FECHA))
        )
    }
}