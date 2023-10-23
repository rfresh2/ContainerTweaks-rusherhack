package ct.module;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.accessors.gui.IMixinAbstractContainerScreen;
import org.rusherhack.client.api.events.client.input.EventMouse;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.setting.BindSetting;
import org.rusherhack.client.api.utils.InventoryUtils;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;

public class ContainerTweaks extends ToggleableModule {
    final BooleanSetting dragMove = new BooleanSetting("DragMove", true);
    final BindSetting dragMoveBind = new BindSetting("HoldKey", RusherHackAPI.getBindManager().parseKey("LEFT_SHIFT"));
    final BooleanSetting quickMove = new BooleanSetting("QuickMove", true);
    final BindSetting quickMoveBind = new BindSetting("HoldKey", RusherHackAPI.getBindManager().parseKey("LEFT_CONTROL"));
    final BooleanSetting dragPickup = new BooleanSetting("DragPickup", true);
    private boolean dragging = false;

    public ContainerTweaks() {
        super("ContainerTweaks", "Simple tweaks for moving items in containers", ModuleCategory.MISC);
        dragMove.addSubSettings(dragMoveBind);
        quickMove.addSubSettings(quickMoveBind);
        registerSettings(dragMove, quickMove, dragPickup);
    }

    @Override
    public void onDisable() {
        dragging = false;
    }

    @Subscribe
    public void dragMove(final EventMouse.Move event) {
        if (!dragMove.getValue() || !dragging || !dragMoveBind.getValue().isKeyDown()) return;
        if (mc.screen instanceof AbstractContainerScreen handler) {
            Slot hoveredSlot = ((IMixinAbstractContainerScreen) handler).getHoveredSlot();
            if (hoveredSlot == null) return;
            InventoryUtils.clickSlot(hoveredSlot.index, true);
        }
    }

    @Subscribe
    public void dragPickup(final EventMouse.Move event) {
        if (!dragPickup.getValue() || !dragging) return;
        if (mc.screen instanceof AbstractContainerScreen handler) {
            ItemStack mouseStack = mc.player.containerMenu.getCarried();
            if (mouseStack.isEmpty()) return;
            Slot hoveredSlot = ((IMixinAbstractContainerScreen) handler).getHoveredSlot();
            if (hoveredSlot == null) return;
            if (mouseStack.getCount() + hoveredSlot.getItem().getCount() > mouseStack.getMaxStackSize()) return;
            InventoryUtils.clickSlot(hoveredSlot.index, false);
            if (hoveredSlot instanceof ResultSlot
                || hoveredSlot instanceof FurnaceResultSlot
                || hoveredSlot instanceof MerchantResultSlot) return;
            InventoryUtils.clickSlot(hoveredSlot.index, false);
        }
    }

    @Subscribe(stage = Stage.PRE)
    public void quickMove(final EventMouse.Key event) {
        if (!quickMove.getValue() || event.getAction() != 0) return;
        if (mc.screen instanceof AbstractContainerScreen handler && event.getButton() == 0 && quickMoveBind.getValue().isKeyDown()) {
            Slot hoveredSlot = ((IMixinAbstractContainerScreen) handler).getHoveredSlot();
            if (hoveredSlot == null) return;
            ItemStack mouseStack = mc.player.containerMenu.getCarried();
            if (mouseStack.isEmpty()) {
                InventoryUtils.clickSlot(hoveredSlot.index, false);
                mouseStack = mc.player.containerMenu.getCarried();
            }
            for(Slot slot : handler.getMenu().slots) {
                if (slot != null
                    && slot.mayPickup(mc.player)
                    && slot.hasItem()
                    && slot.container == hoveredSlot.container
                    && AbstractContainerMenu.canItemQuickReplace(slot, mouseStack, true)) {
                    InventoryUtils.clickSlot(slot.index, true);
                }
            }
            InventoryUtils.clickSlot(hoveredSlot.index, false);
            InventoryUtils.clickSlot(hoveredSlot.index, true);
        }
    }

    @Subscribe(stage = Stage.PRE)
    public void updateDrag(final EventMouse.Key event) {
        if (event.getButton() != 0) return;
        switch (event.getAction()) {
            case GLFW.GLFW_PRESS -> dragging = true;
            case GLFW.GLFW_RELEASE -> dragging = false;
        }
    }
}
