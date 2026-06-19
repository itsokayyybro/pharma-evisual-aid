package com.pharma.evisualaid.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    /** All catalogs, newest first. Filtering/sorting/search is applied in the ViewModel. */
    @Query("SELECT * FROM catalogs ORDER BY uploadedAt DESC")
    fun observeAll(): Flow<List<CatalogEntity>>

    @Query("SELECT * FROM catalogs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CatalogEntity?

    @Query("SELECT * FROM catalogs WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<CatalogEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(catalog: CatalogEntity)

    @Update
    suspend fun update(catalog: CatalogEntity)

    @Query("UPDATE catalogs SET displayName = :name WHERE id = :id")
    suspend fun updateDisplayName(id: String, name: String)

    @Query("UPDATE catalogs SET tags = :tagsJson WHERE id = :id")
    suspend fun updateTags(id: String, tagsJson: String)

    @Query("UPDATE catalogs SET thumbnailPath = :path WHERE id = :id")
    suspend fun updateThumbnail(id: String, path: String)

    @Query("UPDATE catalogs SET lastOpenedAt = :openedAt, lastOpenedPage = :page WHERE id = :id")
    suspend fun updateLastOpened(id: String, openedAt: Long, page: Int)

    @Delete
    suspend fun delete(catalog: CatalogEntity)

    @Query("DELETE FROM catalogs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM catalogs")
    suspend fun deleteAll()
}
