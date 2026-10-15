package com.nerdfm.game

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Persistence {
    private fun file(ctx: Context) = File(ctx.filesDir, "career1.dat")

    fun exists(ctx: Context): Boolean = file(ctx).exists()

    fun save(ctx: Context, w: World): Boolean {
        return try {
            val tmp = File(ctx.filesDir, "career1.tmp")
            ObjectOutputStream(GZIPOutputStream(FileOutputStream(tmp))).use { it.writeObject(w) }
            val f = file(ctx)
            if (f.exists()) f.delete()
            tmp.renameTo(f)
        } catch (e: Exception) {
            false
        }
    }

    fun load(ctx: Context): World? {
        return try {
            ObjectInputStream(GZIPInputStream(FileInputStream(file(ctx)))).use { it.readObject() as World }
        } catch (e: Exception) {
            null
        }
    }

    fun delete(ctx: Context) {
        file(ctx).delete()
    }
}
