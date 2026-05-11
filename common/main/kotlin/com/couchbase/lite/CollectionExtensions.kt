//
// Copyright (c) 2026 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

@file:OptIn(ExperimentalSerializationApi::class)

package com.couchbase.lite

import com.couchbase.lite.internal.core.C4Document
import com.couchbase.lite.internal.fleece.*
import kotlinx.serialization.*


/** Document model classes must implement this interface.
 *  It adds a [documentMeta] property that's used by Couchbase Lite. */
interface DocumentModel {
    /** This tags the model instance with the document ID and revision it was read from,
     *  which enables conflict detection when it's later saved.
     *  You may read this property, but DO NOT alter it.
     *  It should be implemented as a stored property defaulting to `null`, for example:
     *  `@Transient override var documentMeta: DocumentMeta? = null` */
    @Transient var documentMeta: DocumentMeta?
}

/** Stores the Couchbase Lite metadata of a document. Used by the [DocumentModel] interface. */
class DocumentMeta internal constructor(val collection: Collection?, // [Result] leaves it null
                                        val id: String,
                                        val revisionID: String)


/** Gets an existing document with the given ID, and uses Kotlin Serialization to create an
 *  instance of class [T] from it. [T] must implement [DocumentModel].
 *  If a document with the given ID doesn't exist in the collection, returns null. */
@ExperimentalSerializationApi
inline fun <reified T: DocumentModel> Collection.getDocumentAs(id: String): T? =
    getDocumentAs(id, serializer())

@ExperimentalSerializationApi
fun <T: DocumentModel> Collection.getDocumentAs(id: String, deserializer: DeserializationStrategy<T>): T? =
    modelFromC4Doc(this, id, getC4Document(id), deserializer)


/** Saves a [DocumentModel] instance as a document in the collection, with a specified conflict handler.
 *  If the model's [DocumentModel.documentMeta] property is null, it will be saved as a new document with the
 *  given [docID], which must not be null.
 *  Otherwise the [DocumentModel.documentMeta] property determines the document ID and prior revision ID, and the
 *  [docID] parameter should be null.
 *  After a successful save, the [DocumentModel.documentMeta] property is updated to the current state. */
@ExperimentalSerializationApi
inline fun <reified T: DocumentModel> Collection.save(model: T,
                                                      docID: String? = null,
                                                      noinline conflictHandler: ModelConflictHandler<T>? = null) =
    save(model, serializer(), serializer(), docID, conflictHandler)

@ExperimentalSerializationApi
fun <T: DocumentModel> Collection.save(model: T,
                                       serializer: SerializationStrategy<T>,
                                       deserializer: DeserializationStrategy<T>,
                                       docID: String? = null,
                                       conflictHandler: ModelConflictHandler<T>? = null): Boolean
{
    // Get or create the Document:
    val meta = model.documentMeta
    val doc: MutableDocument
    if (meta == null) {
        require(docID != null) { "docID argument must be given when saving a new document" }
        doc = MutableDocument(docID)
    } else {
        require(meta.collection == this || meta.collection == null) {"saving document to wrong collection"}
        require(docID == null || docID == meta.id) {"docID parameter does not match documentMeta.id"}
        doc = getDocument(meta.id)?.toMutable() ?: MutableDocument(meta.id)
    }

    // Subroutine that calls the ModelConflictHandler & updates the model accordingly:
    fun handleConflict(doc: MutableDocument?, curDoc: Document?): Boolean {
        val curModel = curDoc?.let {modelFromC4Doc(this, it.id, it.c4doc, deserializer)}
        val ok = conflictHandler!!(model, curModel)
        if (ok)
            doc?.setContentFromModel(model, serializer)
        return ok
    }

    if (doc.revisionID != meta?.revisionID) {
        // Model is out of date -- have to resolve the conflict
        if (!handleConflict(null, doc))
            return false
    }

    // Replace the document's content with the serialized model:
    if (doc.collection == null) {
        doc.collection = this
    }
    doc.setContentFromModel(model, serializer)

    // Save:
    val ok = if (conflictHandler != null) {
        save(doc) {savingDoc, curDoc -> handleConflict(savingDoc, curDoc) }
    } else {
        save(doc)
        true
    }
    if (ok)
        model.documentMeta = DocumentMeta(this, doc.id, doc.revisionID!!)
    return ok
}


/** Model-based conflict handler callback, used by [Collection.save] with [DocumentModel] objects.
 *  The first parameter is the [DocumentModel] you are saving.
 *  The second parameter is a [DocumentModel] deserialized from the conflicting revision in the collection,
 *  or null if the document has been deleted.
 *
 *  The function may modify the first [DocumentModel] -- the one being saved -- to incorporate changes from
 *  the other [DocumentModel] (the revision in the database), then return true. (But it should NOT modify
 *  its [DocumentModel.documentMeta] property.)
 *
 *  Or it may return false to signal that it can't handle the conflict. */
typealias ModelConflictHandler<T> = (T, T?)-> Boolean


/** Deletes a model's document from the collection.
 *  @throws CouchbaseLiteException if the [DocumentModel.documentMeta] property is null. */
fun Collection.delete(model: DocumentModel, concurrencyControl: ConcurrencyControl = ConcurrencyControl.LAST_WRITE_WINS): Boolean {
    val meta = model.documentMeta ?: throw CouchbaseLiteException("DocumentModel has no document ID")
    require(meta.collection == this || meta.collection == null) {"deleting document from wrong collection"}
    val doc = getDocument(meta.id) ?: return true
    if (doc.revisionID != meta.revisionID && concurrencyControl == ConcurrencyControl.FAIL_ON_CONFLICT)
        return false
    if (!delete(doc, concurrencyControl))
        return false
    model.documentMeta = null
    return true
}


/** Purges a model's document from the collection.
 *  @throws CouchbaseLiteException if the [DocumentModel.documentMeta] property is null,
 *                                 or the document doesn't exist in the collection. */
fun Collection.purge(model: DocumentModel) {
    val id = model.documentMeta?.id ?: throw CouchbaseLiteException("DocumentModel has no document ID")
    purge(id)
    model.documentMeta = null
}


/** Creates a [DocumentModel] instance from a [C4Document]. */
private fun <T:DocumentModel> modelFromC4Doc(collection: Collection,
                                             docID: String,
                                             c4doc: C4Document?,
                                             deserializer: DeserializationStrategy<T>): T?
{
    if (c4doc == null || c4doc.isDocDeleted) return null
    val properties = c4doc.selectedBody2 ?: return null
    val model = deserializeFromFleece(properties.toFLValue(), deserializer)
    model.documentMeta = DocumentMeta(collection, docID, c4doc.revID!!)
    return model
}


/** Extension of [MutableDocument], that updates its content from a [DocumentModel] object. */
private fun <T:DocumentModel> MutableDocument.setContentFromModel(model: T, serializer: SerializationStrategy<T>) {
    val body = serializeToFleece(serializer, model)
    val root = FLValue.fromData(body).asFLDict()
    setContent(root, false)
}
