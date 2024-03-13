import arc.*;
import mindustry.game.EventType;
import mindustry.mod.*;
import ui.ModSettings;

public class SyncDataMod extends Mod {
    public SyncDataMod() {
        Events.on(EventType.ClientLoadEvent.class, e -> {
            ModSettings.init();
        });
    }
}