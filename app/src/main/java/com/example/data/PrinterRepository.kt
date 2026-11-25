package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PrinterRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeTemplates(userId: String): Flow<List<ReceiptTemplate>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/templates"
        emitAll(
            db.collection("users").document(uid).collection("templates")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ReceiptTemplate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeOrders(userId: String): Flow<List<PrintOrder>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch" }
        val path = "users/$uid/orders"
        emitAll(
            db.collection("users").document(uid).collection("orders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PrintOrder::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveTemplate(template: ReceiptTemplate): Result<String> {
        val uid = auth.currentUser?.uid
        val isCreate = template.id.isBlank()
        var docPath = "users/${uid ?: "unauthenticated"}/templates/${template.id}"
        return try {
            val activeUid = requireUserId()
            val cleanName = template.name.trim().take(60)
            val cleanBiz = template.businessName.trim().take(80)
            require(cleanName.isNotEmpty()) { "Template name is required" }
            require(cleanBiz.isNotEmpty()) { "Business name is required" }
            val validWidth = if (template.paperWidthMm == 80L) 80L else 58L
            val validCurrency = template.currencySymbol.trim().take(5).ifEmpty { "$" }
            val cleanItems = template.defaultItems
                .filter { it.name.isNotBlank() }
                .take(25)
                .map { it.toMap() }

            val colRef = db.collection("users").document(activeUid).collection("templates")
            if (isCreate) {
                val docRef = colRef.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to activeUid,
                    "name" to cleanName,
                    "businessName" to cleanBiz,
                    "businessSubtitle" to template.businessSubtitle.trim().take(120),
                    "headerNote" to template.headerNote.trim().take(200),
                    "footerNote" to template.footerNote.trim().take(200),
                    "paperWidthMm" to validWidth,
                    "currencySymbol" to validCurrency,
                    "showDate" to template.showDate,
                    "showLogoHeader" to template.showLogoHeader,
                    "defaultItems" to cleanItems,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = colRef.document(template.id)
                docPath = docRef.path
                val updatePayload = mapOf(
                    "name" to cleanName,
                    "businessName" to cleanBiz,
                    "businessSubtitle" to template.businessSubtitle.trim().take(120),
                    "headerNote" to template.headerNote.trim().take(200),
                    "footerNote" to template.footerNote.trim().take(200),
                    "paperWidthMm" to validWidth,
                    "currencySymbol" to validCurrency,
                    "showDate" to template.showDate,
                    "showLogoHeader" to template.showLogoHeader,
                    "defaultItems" to cleanItems,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
                Result.success(template.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isCreate) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTemplate(templateId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/templates/$templateId"
        return try {
            val uid = requireUserId()
            require(templateId.isNotBlank()) { "Template ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("templates").document(templateId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createOrder(order: PrintOrder): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders"
        return try {
            val uid = requireUserId()
            val colRef = db.collection("users").document(uid).collection("orders")
            val docRef = colRef.document()
            docPath = docRef.path

            val cleanTitle = order.title.trim().take(80).ifEmpty { "Print Job" }
            val cleanOrderNum = order.orderNumber.trim().take(30).ifEmpty {
                "ORD-${(System.currentTimeMillis() % 100000L)}"
            }
            val validWidth = if (order.paperWidthMm == 80L) 80L else 58L
            val validCurrency = order.currencySymbol.trim().take(5).ifEmpty { "$" }
            val cleanItems = order.items
                .filter { it.name.isNotBlank() }
                .take(50)
                .map { it.toMap() }
            val computedTotal = if (cleanItems.isNotEmpty()) {
                order.items.filter { it.name.isNotBlank() }.take(50).sumOf { it.lineTotalCents }
            } else {
                order.totalCents.coerceIn(0L, 1_000_000_000L)
            }

            val payload = mapOf(
                "userId" to uid,
                "orderNumber" to cleanOrderNum,
                "orderType" to order.resolvedOrderType().wireValue,
                "title" to cleanTitle,
                "businessName" to order.businessName.trim().take(80),
                "businessSubtitle" to order.businessSubtitle.trim().take(120),
                "footerNote" to order.footerNote.trim().take(200),
                "textContent" to order.textContent.trim().take(2000),
                "items" to cleanItems,
                "totalCents" to computedTotal.coerceIn(0L, 1_000_000_000L),
                "currencySymbol" to validCurrency,
                "paperWidthMm" to validWidth,
                "printerName" to order.printerName.trim().take(80),
                "printerAddress" to order.printerAddress.trim().take(32),
                "status" to order.resolvedStatus().wireValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderPrintStatus(
        orderId: String,
        newStatus: OrderStatus,
        wasAlreadyPrinted: Boolean,
        printerName: String,
        printerAddress: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders/$orderId"
        return try {
            val uid = requireUserId()
            require(orderId.isNotBlank()) { "Order ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("orders").document(orderId)
            docPath = docRef.path

            val payload = if (wasAlreadyPrinted) {
                // Terminal state lock: keep status = 'printed'
                mapOf(
                    "printerName" to printerName.trim().take(80),
                    "printerAddress" to printerAddress.trim().take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                mapOf(
                    "status" to newStatus.wireValue,
                    "printerName" to printerName.trim().take(80),
                    "printerAddress" to printerAddress.trim().take(32),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteOrder(orderId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/orders/$orderId"
        return try {
            val uid = requireUserId()
            require(orderId.isNotBlank()) { "Order ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("orders").document(orderId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedStarterTemplates(): Result<Unit> {
        val starters = listOf(
            ReceiptTemplate(
                name = "Coffee & Bakery 58mm",
                businessName = "Harbor Roast Coffee",
                businessSubtitle = "42 Pier Ave • (555) 204-8891",
                headerNote = "Freshly Roasted Daily",
                footerNote = "Thank you! Visit us again.",
                paperWidthMm = 58L,
                currencySymbol = "$",
                showDate = true,
                showLogoHeader = true,
                defaultItems = listOf(
                    ReceiptLineItem("Oat Flat White", 1L, 475L),
                    ReceiptLineItem("Almond Croissant", 2L, 380L)
                )
            ),
            ReceiptTemplate(
                name = "Retail Store 80mm",
                businessName = "Metro Market & Goods",
                businessSubtitle = "180 Central Blvd • Tax ID #88-4120",
                headerNote = "Customer Copy",
                footerNote = "Returns accepted within 14 days with receipt.",
                paperWidthMm = 80L,
                currencySymbol = "$",
                showDate = true,
                showLogoHeader = true,
                defaultItems = listOf(
                    ReceiptLineItem("Thermal Paper Roll 80mm", 2L, 550L),
                    ReceiptLineItem("USB-C Cable 1.5m", 1L, 1299L)
                )
            )
        )
        for (tpl in starters) {
            val result = saveTemplate(tpl)
            if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: Exception("Failed to seed template"))
        }
        return Result.success(Unit)
    }
}
