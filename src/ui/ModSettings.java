package ui;

import arc.Core;
import arc.Files;
import arc.files.Fi;
import arc.scene.Group;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import gdrive.GDriveController;
import mindustry.core.GameState;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static mindustry.Vars.*;

public class ModSettings {
    private static final String BASE_DATA_FILE_NAME = "/SyncDataMod-MindustryData";
    private static final String DATA_DIRECTORY = Core.settings.getDataDirectory().path();
    private static final String CREDENTIALS_FILE_PATH =
            ((android) ? "client_secret_android.json" : "client_secret_desktop.json");
    private static final String TOKENS_FILE_PATH =
            DATA_DIRECTORY + "/SyncDataMod/tokens/";
    static BaseDialog gdriveDialog = new BaseDialog("@gdrive-dialog");
    static SettingsMenuDialog smd = new SettingsMenuDialog();
    static GDriveController gdrive = new GDriveController(CREDENTIALS_FILE_PATH, TOKENS_FILE_PATH);

    public static void init() {
        gdriveDialog.addCloseButton();

        gdriveDialog.cont.table(Tex.button, t -> {
            t.defaults().size(280f, 60f).left();
            TextButton.TextButtonStyle style = Styles.flatt;

            t.row();
            t.button("@btn-export", Icon.upload, style, () -> {
                try {
                    exportDataToGD();
                } catch (IOException | ExecutionException | InterruptedException e) {
                    Log.err(e);
                    ui.showException(e);
                }
            });
            t.row();
            t.button("@btn-import", Icon.download, style, () -> ui.showConfirm("@confirm", "@data.import.confirm", () -> {
                try {
                    importDataFromGD();
                } catch (GeneralSecurityException | IOException | InterruptedException e) {
                    Log.err(e);
                    ui.showException(e);
                }
            }));
            t.row();
            t.button("@btn-delete-gdcreds", Icon.trash, style, () -> {
                Fi credentials = new Fi(TOKENS_FILE_PATH + "StoredCredential");
                if (credentials.delete()) ui.showInfo("@delete-gdcreds-success");
            });
        });

        BaseDialog dataDialog = null;
        try {
            Field field = ui.settings.getClass().getDeclaredField("dataDialog");
            field.setAccessible(true);
            dataDialog = (BaseDialog) field.get(ui.settings);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.err(e);
            ui.showException(e);
        }

        BaseDialog finalDataDialog = dataDialog;
        Table dD = (Table) ((Group) (finalDataDialog.getChildren().get(1))).getChildren().get(0);

        dD.row();
        dD.button("@btn-gdrive", Icon.settings, Styles.cleart, () -> {
            gdriveDialog.show();
        });
    }

    public static void exportDataToGD() throws IOException, ExecutionException, InterruptedException {
        Log.info("Start export data to Google Drive");

        Fi dataFile = Fi.get(DATA_DIRECTORY + BASE_DATA_FILE_NAME + ".zip");
        smd.exportData(dataFile);
        Log.info("temp data file created " + dataFile.absolutePath());

        CompletableFuture<Void> upload = CompletableFuture.runAsync(() -> {
            try {
                gdrive.uploadFile(dataFile);
            } catch (IOException e) {
                Log.err(e);
                ui.showException(e);
            }
        }).thenRun(() -> {
            Log.info("Export data to Google Drive is completed");
            ui.showInfo("@data.exported");
            dataFile.delete();
            Log.info("temp data file deleted");
        });
    }

    public static void importDataFromGD() throws GeneralSecurityException, IOException, InterruptedException {
        Log.info("Start import data from Google Drive");

        Fi dataFile = Fi.get(DATA_DIRECTORY + BASE_DATA_FILE_NAME + ".zip");

        CompletableFuture<Void> download = CompletableFuture.runAsync(() -> {
            try {
                gdrive.downloadFile(dataFile.name(), dataFile.absolutePath());
            } catch (IOException e) {
                Log.err(e);
                ui.showException(e);
            }
        }).thenRun(() -> {
            smd.importData(Core.files.get(dataFile.absolutePath(), Files.FileType.absolute));
            control.saves.resetSave();
            state = new GameState();
            dataFile.delete();

            Log.info("Import data from google drive is completed, exit...");
            Core.app.exit();
        });
    }
}
