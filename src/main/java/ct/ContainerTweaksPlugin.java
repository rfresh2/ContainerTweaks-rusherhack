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

    @Override
    public String getName() {
        return "ContainerTweaks";
    }

    @Override
    public String getVersion() {
        return "1.3";
    }

    @Override
    public String getDescription() {
        return "Simple tweaks for moving items in containers";
    }

    @Override
    public String[] getAuthors() {
        return new String[]{"rfresh2"};
    }
}
