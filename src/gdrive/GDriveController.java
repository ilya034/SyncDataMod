package gdrive;

import arc.files.Fi;
import arc.util.Log;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;


public class GDriveController {
    private static final String APPLICATION_NAME = "SyncDataMod";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static Drive driveService;
    private static NetHttpTransport HTTP_TRANSPORT;
    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            Log.err(e);
        }
    }

    public String credentialsFilePath;
    public String tokensFilePath;

    public GDriveController(String creds_path, String tokens_path) {
        credentialsFilePath = creds_path;
        tokensFilePath = tokens_path;
    }

    private Credential getCredentials() throws IOException {
        InputStream in = GDriveController.class.getResourceAsStream(credentialsFilePath);

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(tokensFilePath)))
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private Drive getDriveService() throws IOException {
        if (driveService != null) return driveService;

        Credential credential = getCredentials();
        driveService = new Drive.Builder(
                HTTP_TRANSPORT,
                JSON_FACTORY,
                setHttpTimeout(credential))
                .setApplicationName(APPLICATION_NAME)
                .build();

        return driveService;
    }

    private static HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
        return httpRequest -> {
            requestInitializer.initialize(httpRequest);
            httpRequest.setConnectTimeout(3 * 60000);
            httpRequest.setReadTimeout(10 * 60000);
        };
    }

    public void uploadFile(Fi file) throws IOException {
        Drive driveService = getDriveService();
        Log.info("drive service create");

        String pageToken = null;
        do {
            FileList searchResult = driveService.files().list()
                    .setQ("mimeType='application/zip'")
                    .setQ("name='" + file.name() + "'")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name)")
                    .execute();
            for (File fi : searchResult.getFiles()) {
                Log.info("Found a file with the same name, it will be deleted, Id: " + fi.getId());
                driveService.files().delete(fi.getId()).execute();
            }
            pageToken = searchResult.getNextPageToken();
        } while (pageToken != null);

        java.io.File f = new java.io.File(file.absolutePath());
        InputStreamContent mediaContent =
                new InputStreamContent("application/zip", new BufferedInputStream(new FileInputStream(f)));
        mediaContent.setLength(f.length());
        File fileMetadata = new File().setName(f.getName());

        Drive.Files.Create request = driveService.files().create(fileMetadata, mediaContent);
        request.getMediaHttpUploader().setProgressListener(uploading -> {
            switch (uploading.getUploadState()) {
                case INITIATION_STARTED:
                    Log.info("Initiation has started!");
                    break;
                case INITIATION_COMPLETE:
                    Log.info("Initiation is complete!");
                    break;
                case MEDIA_IN_PROGRESS:
                    Log.info("Progress = " + uploading.getProgress());
                    break;
                case MEDIA_COMPLETE:
                    Log.info("Upload is complete!");
            }
        });
        request.execute();
    }

    public void downloadFile(String name, String downloadPath) throws IOException {
        Drive driveService = getDriveService();

        FileList searchResult = driveService.files().list()
                .setQ("mimeType='application/zip'")
                .setSpaces("drive")
                .setFields("nextPageToken, files(id, name)")
                .execute();
        List<File> files = searchResult.getFiles();
        File file = files.stream().filter(f -> f.getName().equals(name)).findFirst().orElse(null);

        OutputStream outputStream = new FileOutputStream(downloadPath);
        if (file == null) {
            Log.err("No output stream");
            throw new RuntimeException("No output stream");
        }

        Log.info("start download data file from google drive");
        driveService.files().get(file.getId()).executeMediaAndDownloadTo(outputStream);
        outputStream.close();
    }
}