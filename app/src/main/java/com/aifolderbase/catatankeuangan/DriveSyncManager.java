package com.aifolderbase.catatankeuangan;

import android.content.Context;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class DriveSyncManager {
    public static final String ROOT_FOLDER_NAME = "AI_folderbase";
    public static final String APP_FOLDER_NAME = "Catatan Keuangan AI";
    private static final String LEDGER_FILE_NAME = "ledger.json";

    private final FinanceDbHelper db;
    private final Drive drive;

    public DriveSyncManager(Context context, String accountName, FinanceDbHelper db) {
        this.db = db;
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singleton(DriveScopes.DRIVE)
        );
        credential.setSelectedAccountName(accountName);
        drive = new Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Catatan Keuangan AI")
                .build();
    }

    public SyncResult sync() throws Exception {
        String appFolderId = ensureAppFolder();
        File ledger = findChild(appFolderId, LEDGER_FILE_NAME, "application/json");

        int imported = 0;
        if (ledger != null) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            drive.files().get(ledger.getId()).executeMediaAndDownloadTo(output);
            String json = output.toString(StandardCharsets.UTF_8.name());
            imported = db.mergeJson(new JSONObject(json));
        }

        JSONObject localJson = db.exportJson();
        byte[] bytes = localJson.toString(2).getBytes(StandardCharsets.UTF_8);
        ByteArrayContent content = new ByteArrayContent("application/json", bytes);
        if (ledger == null) {
            File metadata = new File();
            metadata.setName(LEDGER_FILE_NAME);
            metadata.setMimeType("application/json");
            metadata.setParents(Collections.singletonList(appFolderId));
            drive.files().create(metadata, content).setFields("id,name,modifiedTime").execute();
        } else {
            drive.files().update(ledger.getId(), null, content).setFields("id,name,modifiedTime").execute();
        }

        return new SyncResult(imported, appFolderId);
    }

    private String ensureAppFolder() throws Exception {
        File root = findFolderByName(ROOT_FOLDER_NAME, null);
        if (root == null) {
            root = createFolder(ROOT_FOLDER_NAME, null);
        }

        File app = findFolderByName(APP_FOLDER_NAME, root.getId());
        if (app == null) {
            app = createFolder(APP_FOLDER_NAME, root.getId());
        }
        return app.getId();
    }

    private File findFolderByName(String name, String parentId) throws Exception {
        String query = "mimeType = 'application/vnd.google-apps.folder' and name = '" + escapeQuery(name) + "' and trashed = false";
        if (parentId != null) {
            query += " and '" + parentId + "' in parents";
        }
        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id,name)")
                .setPageSize(1)
                .execute();
        return result.getFiles().isEmpty() ? null : result.getFiles().get(0);
    }

    private File findChild(String parentId, String name, String mimeType) throws Exception {
        String query = "'" + parentId + "' in parents and name = '" + escapeQuery(name) + "' and mimeType = '" + mimeType + "' and trashed = false";
        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id,name,modifiedTime)")
                .setPageSize(1)
                .execute();
        return result.getFiles().isEmpty() ? null : result.getFiles().get(0);
    }

    private File createFolder(String name, String parentId) throws Exception {
        File metadata = new File();
        metadata.setName(name);
        metadata.setMimeType("application/vnd.google-apps.folder");
        if (parentId != null) {
            metadata.setParents(Collections.singletonList(parentId));
        }
        return drive.files().create(metadata).setFields("id,name").execute();
    }

    private String escapeQuery(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    public static class SyncResult {
        public final int importedRecords;
        public final String folderId;

        public SyncResult(int importedRecords, String folderId) {
            this.importedRecords = importedRecords;
            this.folderId = folderId;
        }
    }
}
