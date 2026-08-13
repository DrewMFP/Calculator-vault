package com.calculator.vault.managers

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds

class ContactManager(private val context: Context) {
    
    private val contentResolver: ContentResolver = context.contentResolver
    
    fun importContacts(): Map<String, Any> {
        val contacts = mutableListOf<Map<String, Any>>()
        
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null, null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
            
            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                val name = it.getString(nameIndex) ?: "Unknown"
                val hasPhone = it.getInt(hasPhoneIndex) > 0
                
                val phoneNumbers = if (hasPhone) getPhoneNumbers(id) else emptyList()
                val emails = getEmails(id)
                
                contacts.add(mapOf(
                    "id" to id,
                    "name" to name,
                    "phones" to phoneNumbers,
                    "emails" to emails
                ))
            }
        }
        
        return mapOf("contacts" to contacts, "count" to contacts.size)
    }
    
    private fun getPhoneNumbers(contactId: String): List<String> {
        val phones = mutableListOf<String>()
        
        val cursor = contentResolver.query(
            CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )
        
        cursor?.use {
            val numberIndex = it.getColumnIndex(CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                it.getString(numberIndex)?.let { number -> phones.add(number) }
            }
        }
        
        return phones
    }
    
    private fun getEmails(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        
        val cursor = contentResolver.query(
            CommonDataKinds.Email.CONTENT_URI,
            null,
            "${CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )
        
        cursor?.use {
            val emailIndex = it.getColumnIndex(CommonDataKinds.Email.ADDRESS)
            while (it.moveToNext()) {
                it.getString(emailIndex)?.let { email -> emails.add(email) }
            }
        }
        
        return emails
    }
    
    fun saveContact(name: String, phone: String, email: String): Map<String, Any> {
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            
            val rawContactInsertIndex = ops.size
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build())
            
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build())
            
            if (phone.isNotEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                    .build())
            }
            
            if (email.isNotEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Email.ADDRESS, email)
                    .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK)
                    .build())
            }
            
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            mapOf("success" to true, "message" to "Contact saved")
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
        }
    }
}