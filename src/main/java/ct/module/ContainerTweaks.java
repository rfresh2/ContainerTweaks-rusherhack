package ct.module;

import com.google.common.collect.Lists;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.lwjgl.glfw.GLFW;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.accessors.gui.IMixinAbstractContainerScreen;
import org.rusherhack.client.api.accessors.gui.IMixinScreen;
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
import java.util.List;
import java.util.Set;

public class ContainerTweaks extends ToggleableModule {
    final BooleanSetting dragMove = new BooleanSetting("DragMove", true);
    final BindSetting dragMoveBind = new BindSetting("HoldKey", RusherHackAPI.getBindManager().createKeyboardKey(GLFW.GLFW_KEY_LEFT_SHIFT));
    // Shulkers with larger NBT seem to be most sensitive to multiple moves per tick
    final NumberSetting<Integer> maxDragMovesPerTick = new NumberSetting<>("MaxPerTick", 2, 1, 5);
    final BooleanSetting quickMove = new BooleanSetting("QuickMove", true);
    final BindSetting quickMoveBind = new BindSetting("HoldKey", RusherHackAPI.getBindManager().createKeyboardKey(GLFW.GLFW_KEY_LEFT_CONTROL));
    final BooleanSetting quickMoveAll = new BooleanSetting("MoveAll", "Whether to move only items matching the hovered stack or all items in the container", false);
    final BooleanSetting quickMoveOnlyShulkers = new BooleanSetting("OnlyShulkers", "Whether to only quick move shulkers", false);
    final BooleanSetting quickMoveReverseOrderInventory = new BooleanSetting(
        "ReverseFromInv",
        "Moves items from higher inv slot ids before lower slot id's when moving from player inventory",
        false
    );
    final BooleanSetting quickMoveReverseOrderContainer = new BooleanSetting(
        "ReverseToInv",
        "Moves items from higher inv slot ids before lower slot id's when moving to player inventory",
        false
    );
    final BooleanSetting hijackChestStealer = new BooleanSetting(
        "ChestStealer",
        "Hijack the ChestStealer button actions to use quick move",
        false
    );
    final BooleanSetting dragPickup = new BooleanSetting("DragPickup", true);
    private boolean dragging = false;
    private Set<Integer> dragMovedSlots = new HashSet<>(5);

    public ContainerTweaks() {
        super("ContainerTweaks", "Simple tweaks for moving items in containers", ModuleCategory.MISC);
        dragMove.addSubSettings(dragMoveBind, maxDragMovesPerTick);
        quickMove.addSubSettings(quickMoveBind,
                                 quickMoveAll,
                                 quickMoveOnlyShulkers,
                                 quickMoveReverseOrderInventory,
                                 quickMoveReverseOrderContainer,
                                 hijackChestStealer);
        registerSettings(dragMove, quickMove, dragPickup);
    }

    @Override
    public void onDisable() {
        dragging = false;
    }

    private Button stealButton = null;
    private Button fillButton = null;

    @Subscribe(stage = Stage.PRE)
    public void tick(EventUpdate event) {
        dragMovedSlots.clear();
        if (quickMove.getValue()
            && hijackChestStealer.getValue()
            && mc.screen instanceof AbstractContainerScreen handler
            && (fillButton == null || stealButton == null)
        ) {
            Button rhStealButton = null;
            Button rhFillButton = null;
            for (GuiEventListener child : handler.children()) {
                if (child instanceof Button b && b != stealButton && b != fillButton) {
                    var buttonMessage = b.getMessage().getString();
                    if (buttonMessage.equals("Steal"))
                        rhStealButton = b;
                    else if (buttonMessage.equals("Fill"))
                        rhFillButton = b;
                }
            }
            if (rhStealButton != null) {
                stealButton = Button.builder(rhStealButton.getMessage(), button -> {
                        handler.getMenu().slots.stream().findFirst().ifPresent(slot -> {
                            var fromContainer = slot.container;
                            chestStealerQuickMove(fromContainer, handler);
                        });
                    })
                    .pos(rhStealButton.getX(), rhStealButton.getY())
                    .size(rhStealButton.getWidth(), rhStealButton.getHeight())
                    .build();
                ((IMixinScreen) handler).invokeRemoveWidget(rhStealButton);
                ((IMixinScreen) handler).invokeAddRenderableWidget(stealButton);
            }
            if (rhFillButton != null) {
                fillButton = Button.builder(rhFillButton.getMessage(), button -> {
                        var fromContainer = mc.player.getInventory();
                        chestStealerQuickMove(fromContainer, handler);
                    })
                    .pos(rhFillButton.getX(), rhFillButton.getY())
                    .size(rhFillButton.getWidth(), rhFillButton.getHeight())
                    .build();
                ((IMixinScreen) handler).invokeRemoveWidget(rhFillButton);
                ((IMixinScreen) handler).invokeAddRenderableWidget(fillButton);
            }
        } else {
            stealButton = null;
            fillButton = null;
        }
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
                // todo: this state is actually not being seen for some reason
                //  is there processing happening before or during this event in mc code elsewhere?
                //  we do pick up the item from the click but its not this code doing it
                pickup(handler, hoveredSlot.index);
                mouseStack = mc.player.containerMenu.getCarried();
            }
            final boolean isFromPlayerInv = hoveredSlot.container instanceof Inventory;
            for(Slot slot : getQuickMoveSlotList(handler, isFromPlayerInv)) {
                if (slot != null
                    && slot.mayPickup(mc.player)
                    && slot.hasItem()
                    && slot.container == hoveredSlot.container
                    && (quickMoveAll.getValue() || AbstractContainerMenu.canItemQuickReplace(slot, mouseStack, true))
                ) {
                    if (quickMoveOnlyShulkers.getValue()
                        && (!(slot.getItem().getItem() instanceof BlockItem blockItem)
                        || !(blockItem.getBlock() instanceof ShulkerBoxBlock)))
                        continue;
                    quickMove(handler, slot.index);
                }
            }
            pickup(handler, hoveredSlot.index);
            quickMove(handler, hoveredSlot.index);
        }
    }

    public void chestStealerQuickMove(
        final Container fromContainer,
        final AbstractContainerScreen handler
    ) {
        final boolean isFromPlayerInv = fromContainer instanceof Inventory;
        for(Slot slot : getQuickMoveSlotList(handler, isFromPlayerInv)) {
            if (slot != null
                && slot.mayPickup(mc.player)
                && slot.hasItem()
                && slot.container == fromContainer
            ) {
                if (quickMoveOnlyShulkers.getValue()
                    && (!(slot.getItem().getItem() instanceof BlockItem blockItem)
                    || !(blockItem.getBlock() instanceof ShulkerBoxBlock)))
                    continue;
                quickMove(handler, slot.index);
            }
        }
    }

    public List<Slot> getQuickMoveSlotList(final AbstractContainerScreen containerScreen, final boolean isFromPlayerInv) {
        final List<Slot> slots = containerScreen.getMenu().slots;
        if (isFromPlayerInv)
            if (quickMoveReverseOrderInventory.getValue())
                return Lists.reverse(slots);
            else
                return slots;
        else
            if (quickMoveReverseOrderContainer.getValue())
                return Lists.reverse(slots);
            else
                return slots;
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
