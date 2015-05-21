/*
     Copyright (c) 2015, The Linux Foundation. All Rights Reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.android.cellbroadcastreceiver;

import android.provider.Telephony;
import android.util.Log;
import android.database.Cursor;
import java.util.ArrayList;
import android.telephony.SmsCbCmasInfo;
import android.telephony.SmsCbEtwsInfo;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.content.Context;
import android.telephony.CellBroadcastMessage;
import java.util.Iterator;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;

public class CellBroadcastAlertServiceIDList{
    private static final String TAG = "CellBroadcastAlertServiceUtil";
    private static int TIME12HOURS = 12*60*60*1000;

    /** List of message IDs received for recent 12 hours. */
    private static final ArrayList<CellBroadcastAlertService.MessageServiceCategoryAndScope> s12HIdList =
            new ArrayList<CellBroadcastAlertService.MessageServiceCategoryAndScope>(8);

    public static void initHalfDayCmasList(Context context) {
        long now = System.currentTimeMillis();
        String[] project = new String[] {
                Telephony.CellBroadcasts.PLMN, Telephony.CellBroadcasts.LAC,
                Telephony.CellBroadcasts.CID, Telephony.CellBroadcasts.DELIVERY_TIME,
                Telephony.CellBroadcasts.SERVICE_CATEGORY,
                Telephony.CellBroadcasts.SERIAL_NUMBER, Telephony.CellBroadcasts.MESSAGE_BODY};
        Cursor cursor = context.getContentResolver().query(
                Telephony.CellBroadcasts.CONTENT_URI,project,
                Telephony.CellBroadcasts.DELIVERY_TIME + ">?",
                new String[]{now - TIME12HOURS + ""},
                Telephony.CellBroadcasts.DELIVERY_TIME + " DESC");
        if (s12HIdList != null) {
            s12HIdList.clear();
        }
        CellBroadcastAlertService.MessageServiceCategoryAndScope newCmasId;
        int serviceCategory;
        int serialNumber;
        String messageBody;
        long deliveryTime;
        if(cursor != null){
            int plmnColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.PLMN);
            int lacColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.LAC);
            int cidColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.CID);
            int serviceCategoryColumn = cursor.getColumnIndex(Telephony.CellBroadcasts
                    .SERVICE_CATEGORY);
            int serialNumberColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.SERIAL_NUMBER);
            int messageBodyColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.MESSAGE_BODY);
            int deliveryTimeColumn = cursor.getColumnIndex(Telephony.CellBroadcasts.DELIVERY_TIME);
            while(cursor.moveToNext()){
                String plmn = getStringColumn(plmnColumn, cursor);
                int lac = getIntColumn(lacColumn, cursor);
                int cid = getIntColumn(cidColumn, cursor);
                SmsCbLocation location = new SmsCbLocation(plmn, lac, cid);
                serviceCategory = getIntColumn(serviceCategoryColumn, cursor);
                serialNumber = getIntColumn(serialNumberColumn, cursor);
                messageBody = getStringColumn(messageBodyColumn, cursor);
                deliveryTime = getLongColumn(deliveryTimeColumn, cursor);
                newCmasId = new CellBroadcastAlertService.MessageServiceCategoryAndScope(
                            serviceCategory, serialNumber, location, messageBody, deliveryTime);
                s12HIdList.add(newCmasId);
            }
        }
        if(cursor != null){
            cursor.close();
        }
    }

    public static boolean isDuplicated(SmsCbMessage message) {
        final CellBroadcastMessage cbm = new CellBroadcastMessage(message);
        long lastestDeliveryTime = cbm.getDeliveryTime();
        CellBroadcastAlertService.MessageServiceCategoryAndScope newCmasId = new CellBroadcastAlertService.MessageServiceCategoryAndScope(
                        message.getServiceCategory(), message.getSerialNumber(),
                        message.getLocation(), message.getMessageBody(), lastestDeliveryTime);
        Iterator<CellBroadcastAlertService.MessageServiceCategoryAndScope> iterator = s12HIdList.iterator();
        ArrayList<CellBroadcastAlertService.MessageServiceCategoryAndScope> tempMessageList =
                new ArrayList<CellBroadcastAlertService.MessageServiceCategoryAndScope>();
        boolean duplicatedMessage = false;
        while(iterator.hasNext()){
                CellBroadcastAlertService.MessageServiceCategoryAndScope tempMessage =
                        (CellBroadcastAlertService.MessageServiceCategoryAndScope)iterator.next();
                boolean moreThan12Hour =
                        (lastestDeliveryTime - tempMessage.mDeliveryTime >= TIME12HOURS);
                if (moreThan12Hour) {
                    break;
                } else {
                    tempMessageList.add(tempMessage);
                    if (tempMessage.equals(newCmasId)) {
                        duplicatedMessage = true;
                        break;
                    }
                }
            }
            if (duplicatedMessage) {
                if (tempMessageList != null) {
                    tempMessageList.clear();
                    tempMessageList = null;
                }
                return true;
            } else {
                if (s12HIdList != null) {
                    s12HIdList.clear();
                }
                if (tempMessageList != null) {
                    s12HIdList.addAll(tempMessageList);
                    tempMessageList.clear();
                    tempMessageList = null;
                }
                s12HIdList.add(0, newCmasId);
            }
            return false;
    }

    private static String getStringColumn (int column, Cursor cursor) {
        if (column != -1 && !cursor.isNull(column)) {
            return cursor.getString(column);
        } else {
            return null;
        }
    }

    private static int getIntColumn (int column, Cursor cursor) {
        if (column != -1 && !cursor.isNull(column)) {
            return cursor.getInt(column);
        } else {
            return -1;
        }
    }

    private static long getLongColumn (int column, Cursor cursor) {
        if (column != -1 && !cursor.isNull(column)) {
            return cursor.getLong(column);
        } else {
            return -1;
        }
    }

    public static boolean markItemDeleted(long rowId, CellBroadcastContentProvider provider) {
        deleteAllMarked(provider);
        SQLiteDatabase db = provider.getOpenHelper().getWritableDatabase();
        ContentValues value = new ContentValues(1);
        value.put(Telephony.CellBroadcasts.MESSAGE_DELETED, 1);
        int rowCount = db.update(CellBroadcastDatabaseHelper.TABLE_NAME, value,
                Telephony.CellBroadcasts._ID + "=?",
                new String[]{Long.toString(rowId)});
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete broadcast at row " + rowId);
            return false;
        }
    }

    public static boolean markAllItemsDeleted(CellBroadcastContentProvider provider) {
        deleteAllMarked(provider);
        SQLiteDatabase db = provider.getOpenHelper().getWritableDatabase();
        ContentValues value = new ContentValues(1);
        value.put(Telephony.CellBroadcasts.MESSAGE_DELETED, 1);
        int rowCount = db.update(CellBroadcastDatabaseHelper.TABLE_NAME, value,
                Telephony.CellBroadcasts.MESSAGE_DELETED + "=0", null);
        if (rowCount != 0) {
            return true;
        } else {
            Log.e(TAG, "failed to delete all broadcasts");
            return false;
        }
    }

    private static void deleteAllMarked(CellBroadcastContentProvider provider) {
        SQLiteDatabase db = provider.getOpenHelper().getWritableDatabase();
        String strWhere = Telephony.CellBroadcasts.MESSAGE_DELETED +
                "=1 AND "+Telephony.CellBroadcasts.DELIVERY_TIME + "<?";
        long time = System.currentTimeMillis();
        String strExpired = Long.toString(time - TIME12HOURS);
        db.delete(CellBroadcastDatabaseHelper.TABLE_NAME, strWhere,new String[]{strExpired});
    }
}
