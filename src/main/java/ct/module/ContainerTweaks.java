package ct.module;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.accessors.gui.IMixinAbstractContainerScreen;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.client.input.EventMouse;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.setting.BindSetting;
import org.rusherhack.core.event.stage.Stage;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.NumberSetting;

import java.util.HashSet;
import java.util.Set;

public class ContainerTweaks extends ToggleableModule {
    final BooleanSetting dragMove = new BooleanSetting("DragMove", true);
    final BindSetting dragMoveBind = new BindSetting("HoldKey", RusherHackAPI.getBindManager().createKeyboardKey(GLFW.GLFW_KEY_LEFT_SHIFT));
    // Shulkers with larger NBT seem to be most sensitive to multiple moves per tick
    final NumberSetting<Integer> maxDragMovesPerTick = new NumberSetting<>("MaxPerTick", 2, 1, 5);
    final BooleanSetting quickMove = new BooleanSetting("QuickMove", true);
    final BindSetting quickMoveBind = new BindSetting("HoldKey", RusherHackAPI.getBindManager().createKeyboardKey(GLFW.GLFW_KEY_LEFT_CONTROL));
    final BooleanSetting dragPickup = new BooleanSetting("DragPickup", true);
    private boolean dragging = false;
    private Set<Integer> dragMovedSlots = new HashSet<>(5);

    public ContainerTweaks() {
        super("ContainerTweaks", "Simple tweaks for moving items in containers", ModuleCategory.MISC);
        dragMove.addSubSettings(dragMoveBind, maxDragMovesPerTick);
        quickMove.addSubSettings(quickMoveBind);
        registerSettings(dragMove, quickMove, dragPickup);
    }

    @Override
    public void onDisable() {
        dragging = false;
    }

    @Subscribe(stage = Stage.PRE)
    public void tick(EventUpdate event) {
        dragMovedSlots.clear();
    }

    @Subscribe
    public void dragMove(final EventMouse.Move event) {
        if (!dragMove.getValue() || !dragging || !dragMoveBind.getValue().isKeyDown()) return;
        if (mc.screen instanceof AbstractContainerScreen handler) {
            Slot hoveredSlot = ((IMixinAbstractContainerScreen) handler).getHoveredSlot();
            if (hoveredSlot == null) return;
            // this is invoked more frequently than once per tick
            // i think it wouldn't be an issue if we hooked into the screen's mouse drag event
            if (dragMovedSlots.size() > maxDragMovesPerTick.getValue()) return;
            if (dragMovedSlots.contains(hoveredSlot.index)) return;
            quickMove(handler, hoveredSlot.index);
            dragMovedSlots.add(hoveredSlot.index);
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
            if (handler instanceof CraftingScreen craftingScreen && hoveredSlot.index < craftingScreen.getMenu().getSize()) return;
            if (handler instanceof InventoryScreen && hoveredSlot.index < 5) return;
            if (mouseStack.getCount() + hoveredSlot.getItem().getCount() > mouseStack.getMaxStackSize()) return;
            pickup(handler, hoveredSlot.index);
            if (hoveredSlot instanceof ResultSlot
                || hoveredSlot instanceof FurnaceResultSlot
                || hoveredSlot instanceof MerchantResultSlot) return;
            pickup(handler, hoveredSlot.index);
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
                // todo: improve reliability for empty moves with large NBT shulkers on 2b2t
                //  we may need to schedule the quick moves on the next tick
                pickup(handler, hoveredSlot.index);
                mouseStack = mc.player.containerMenu.getCarried();
            }
            for(Slot slot : handler.getMenu().slots) {
                if (slot != null
                    && slot.mayPickup(mc.player)
                    && slot.hasItem()
                    && slot.container == hoveredSlot.container
                    && AbstractContainerMenu.canItemQuickReplace(slot, mouseStack, true)) {
                    quickMove(handler, slot.index);
                }
            }
            pickup(handler, hoveredSlot.index);
            quickMove(handler, hoveredSlot.index);
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

    // avoiding InventoryUtils.clickSlot as it ticks the network connection on every call for some reason
    // that's fine if you do it once per tick but not for multiple clicks per tick, at least on strict servers
    public void clickSlot(AbstractContainerScreen screen, int slotId, ClickType clickType) {
        if (mc.gameMode == null) return;
        mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotId, 0, clickType, mc.player);
    }

    public void pickup(AbstractContainerScreen screen, int slotId) {
        clickSlot(screen, slotId, ClickType.PICKUP);
    }

    public void quickMove(AbstractContainerScreen screen, int slotId) {
        clickSlot(screen, slotId, ClickType.QUICK_MOVE);
    }
}
