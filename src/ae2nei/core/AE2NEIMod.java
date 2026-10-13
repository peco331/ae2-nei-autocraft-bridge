package ae2nei.core;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import ae2nei.bridge.BridgeNetwork;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;
import java.util.Map;

@Mod(
    modid = "ae2neiautocraftbridge",
    name = "AE2 NEI AutoCraft Bridge",
    version = "1.1.3",
    dependencies = "after:NotEnoughItems;after:appliedenergistics2"
)
public class AE2NEIMod {

    @NetworkCheckHandler
    public boolean compatible(Map<String, String> remote, Side side) {
        return "1.1.3".equals(remote.get("ae2neiautocraftbridge"));
    }

    @Mod.EventHandler
    public void init(FMLPreInitializationEvent event) {
        BridgeNetwork.init();
        System.out.println("[AE2-NEI-Bridge] Protocol 1.1.3 initialized (server-confirmed crafting).");
    }
}
