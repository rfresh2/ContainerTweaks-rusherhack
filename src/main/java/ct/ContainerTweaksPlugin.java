package ct;

import ct.module.ContainerTweaks;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

public class ContainerTweaksPlugin extends Plugin {
    @Override
    public void onLoad() {
        RusherHackAPI.getModuleManager().registerFeature(new ContainerTweaks());
        getLogger().info("ContainerTweaks plugin loaded");
    }

    @Override
    public void onUnload() {

    }
}
