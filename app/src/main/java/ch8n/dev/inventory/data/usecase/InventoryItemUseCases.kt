package ch8n.dev.inventory.data.usecase

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import ch8n.dev.inventory.UseCaseScope
import ch8n.dev.inventory.data.DataModule
import ch8n.dev.inventory.data.database.firestorage.RemoteUploadDAO
import ch8n.dev.inventory.data.database.firestore.InventoryItemFS
import ch8n.dev.inventory.data.database.firestore.RemoteItemDAO
import ch8n.dev.inventory.data.database.roomdb.InventoryItemEntity
import ch8n.dev.inventory.data.database.roomdb.LocalItemDAO
import ch8n.dev.inventory.data.domain.InventoryCategory
import ch8n.dev.inventory.data.domain.InventoryItem
import ch8n.dev.inventory.data.domain.InventorySupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


fun InventoryItem.toRemote(): InventoryItemFS {
    return InventoryItemFS(
        documentReferenceId = uid,
        itemName = itemName,
        itemCategoryDocumentReferenceId = itemCategoryId,
        itemImage = itemImage,
        itemQuantity = itemQuantity,
        itemWeight = itemWeight,
        itemSupplierDocumentReferenceId = itemSupplierId,
        itemSellingPrice = itemSellingPrice,
        itemPurchasePrice = itemPurchasePrice,
        itemSize = itemSize,
        itemColor = itemColor
    )
}

fun InventoryItemFS.toEntity(): InventoryItemEntity {
    return InventoryItemEntity(
        uid = documentReferenceId,
        itemName = itemName,
        itemCategoryId = itemCategoryDocumentReferenceId,
        itemImage = itemImage,
        itemQuantity = itemQuantity,
        itemWeight = itemWeight,
        itemSupplierId = itemSupplierDocumentReferenceId,
        itemSellingPrice = itemSellingPrice,
        itemPurchasePrice = itemPurchasePrice,
        itemSize = itemSize,
        itemColor = itemColor
    )
}

fun InventoryItemEntity.toView(): InventoryItem {
    return InventoryItem(
        uid = uid,
        itemName = itemName,
        itemCategoryId = itemCategoryId,
        itemImage = itemImage,
        itemQuantity = itemQuantity,
        itemWeight = itemWeight,
        itemSupplierId = itemSupplierId,
        itemSellingPrice = itemSellingPrice,
        itemPurchasePrice = itemPurchasePrice,
        itemSize = itemSize,
        itemColor = itemColor
    )
}

fun InventoryItem.toEntity(): InventoryItemEntity {
    return InventoryItemEntity(
        uid = uid,
        itemName = itemName,
        itemCategoryId = itemCategoryId,
        itemImage = itemImage,
        itemQuantity = itemQuantity,
        itemWeight = itemWeight,
        itemSupplierId = itemSupplierId,
        itemSellingPrice = itemSellingPrice,
        itemPurchasePrice = itemPurchasePrice,
        itemSize = itemSize,
        itemColor = itemColor
    )
}


class GetInventoryItem(
    private val remoteItemDAO: RemoteItemDAO = DataModule.Injector.remoteDatabase.remoteItemDAO,
    private val localItemDAO: LocalItemDAO = DataModule.Injector.localDatabase.localItemDAO(),
) : UseCaseScope {

    val local = localItemDAO.getAll()
        .distinctUntilChanged()
        .map { entities ->
            entities.map { it.toView() }
        }

    fun invalidate() {
        launch(NonCancellable) {
            val remoteItems = remoteItemDAO.getAllItems()
            localItemDAO.insertAll(*remoteItems.map { it.toEntity() }.toTypedArray())
        }
    }

    fun categoryFilter(
        searchQuery: String,
        selectedCategory: InventoryCategory
    ): Flow<List<InventoryItem>> {
        return local.map { items ->
            val result = items.filter {
                if (selectedCategory.id.isEmpty()) return@filter true
                return@filter it.itemCategoryId == selectedCategory.id
            }.filter {
                if (searchQuery.isNotEmpty()) {
                    return@filter it.itemName.contains(searchQuery, ignoreCase = true)
                } else {
                    true
                }
            }
            Log.d("ch8n", "GetInventoryItem filter: $result")
            result
        }.flowOn(Dispatchers.IO)
    }

    fun supplierFilter(
        searchQuery: String,
        selectedSupplier: InventorySupplier
    ): Flow<List<InventoryItem>> {
        return local.map { items ->
            val result = items.filter {
                if (selectedSupplier.id.isEmpty()) return@filter true
                return@filter it.itemSupplierId == selectedSupplier.id
            }.filter {
                if (searchQuery.isNotEmpty()) {
                    return@filter it.itemName.contains(searchQuery, ignoreCase = true)
                } else {
                    true
                }
            }
            Log.d("ch8n", "GetInventoryItem filter: $result")
            result
        }.flowOn(Dispatchers.IO)
    }
}


class UpsertInventoryItem(
    private val remoteItemDAO: RemoteItemDAO = DataModule.Injector.remoteDatabase.remoteItemDAO,
    private val localItemDAO: LocalItemDAO = DataModule.Injector.localDatabase.localItemDAO(),
    private val uploadFileDAO: RemoteUploadDAO = DataModule.Injector.remoteDatabase.remoteUploadDAO,
    private val applicationContext: Context = DataModule.Injector.appContext,
) : UseCaseScope {
    fun execute(
        item: InventoryItem
    ) {
        launch(NonCancellable) {
            val remoteUrl = uploadFileDAO.getImageUrl(applicationContext, item.itemImage.toUri())
            val remoteItem = remoteItemDAO.upsertInventoryItem(item.copy(itemImage = remoteUrl))
            val entity = remoteItem.toEntity()
            localItemDAO.insertAll(entity)
        }
    }
}

class DeleteInventoryItem(
    private val remoteItemDAO: RemoteItemDAO = DataModule.Injector.remoteDatabase.remoteItemDAO,
    private val localItemDAO: LocalItemDAO = DataModule.Injector.localDatabase.localItemDAO(),
) : UseCaseScope {
    fun execute(
        itemId: InventoryItem,
    ) {
        launch(NonCancellable) {
            remoteItemDAO.deleteInventoryItem(itemId.uid)
            localItemDAO.delete(itemId.toEntity())
        }
    }
}