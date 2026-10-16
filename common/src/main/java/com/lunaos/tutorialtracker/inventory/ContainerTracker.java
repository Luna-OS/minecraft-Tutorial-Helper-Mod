package com.lunaos.tutorialtracker.inventory;

import com.lunaos.tutorialtracker.TutorialTracker;
import com.lunaos.tutorialtracker.materials.ItemNameIndex;
import com.lunaos.tutorialtracker.project.Project;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Counts what the player has. A client only sees inside a chest while it is open, so assigned containers are
 * counted from the last time they were open: while a tracked container's screen is open, its contents are read
 * again whenever they change. Shulker boxes and bundles are counted with what is in them, wherever they are.
 */
public final class ContainerTracker {

    /** A container block as the project stores it. */
    public record Ref(String world, String dimension, BlockPos pos, boolean ender, String label) {
    }

    /** The container the open screen belongs to, worked out when it opened. */
    private static Screen openScreen;
    private static Ref openRef;
    /** What containers held when last open this session, so assigning one opened before needs no second look. */
    private static final Map<String, Map<String, Integer>> lastSeen = new HashMap<>();

    private static Map<String, Integer> inventoryCounts = Map.of();
    private static Map<String, Integer> totals = Map.of();
    /** Goes up whenever the totals are worked out again, so the HUD knows when to rebuild its rows. */
    private static int revision;

    /*
     * What the last count was made from. The inventory is only counted again when it changed: counting means going
     * through every slot and into every shulker box and bundle, too much to do twenty times a second for nothing.
     */
    private static Object countedPlayer;
    private static int countedInventoryChanges = -1;
    private static ItemStack countedCarried = ItemStack.EMPTY;
    private static int countedCarriedCount;
    private static int ticksSinceCount;
    /** The open container's state when its contents were last read. */
    private static int readStateId = -1;
    private static int ticksSinceRead;

    private ContainerTracker() {
    }

    // --- Where we are -------------------------------------------------------------------------------------------

    /** The world or server: containers are only matched within the same one. */
    public static String worldKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isLocalServer() && minecraft.getSingleplayerServer() != null) {
            return "sp:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null) {
            return "mp:" + server.ip.toLowerCase(java.util.Locale.ROOT);
        }
        return "unknown";
    }

    public static String dimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    /** The container block the player looks at, or null. Double chests always come out as the same half. */
    public static Ref lookedAt() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        String label = state.getBlock().getName().getString();
        if (state.getBlock() instanceof EnderChestBlock) {
            return new Ref(worldKey(), dimension(), pos, true, label);
        }
        if (state.getBlock() instanceof ChestBlock) {
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                Direction toOther = ChestBlock.getConnectedDirection(state);
                BlockPos other = pos.relative(toOther);
                // The lower of the two positions stands for the pair.
                if (other.asLong() < pos.asLong()) {
                    pos = other;
                }
            }
            return new Ref(worldKey(), dimension(), pos.immutable(), false, label);
        }
        if (state.getBlock() instanceof BarrelBlock || state.getBlock() instanceof ShulkerBoxBlock) {
            return new Ref(worldKey(), dimension(), pos.immutable(), false, label);
        }
        return null;
    }

    private static String key(Ref ref) {
        return ref.ender() ? ref.world() + "|ender" : ref.world() + "|" + ref.dimension() + "|" + ref.pos().asLong();
    }

    public static Project.TrackedContainer find(Project project, Ref ref) {
        for (Project.TrackedContainer container : project.containers) {
            if (container.matches(ref.world(), ref.dimension(), ref.pos().getX(), ref.pos().getY(), ref.pos().getZ(),
                ref.ender())) {
                return container;
            }
        }
        return null;
    }

    /** Assigns a container to the project, or removes it. Returns true when it is assigned now. */
    public static boolean toggle(Project project, Ref ref) {
        Project.TrackedContainer existing = find(project, ref);
        if (existing != null) {
            project.containers.remove(existing);
            TutorialTracker.saveProject(project);
            recount();
            return false;
        }
        Project.TrackedContainer added = new Project.TrackedContainer();
        added.world = ref.world();
        added.dimension = ref.dimension();
        added.x = ref.pos().getX();
        added.y = ref.pos().getY();
        added.z = ref.pos().getZ();
        added.ender = ref.ender();
        added.label = ref.label();
        Map<String, Integer> seen = lastSeen.get(key(ref));
        if (seen != null) {
            added.contents = new LinkedHashMap<>(seen);
            added.seen = true;
        }
        project.containers.add(added);
        TutorialTracker.saveProject(project);
        recount();
        return true;
    }

    /** The container behind the screen that is open, when it is one we can track. */
    public static Ref openContainer() {
        return openRef;
    }

    // --- Every tick ---------------------------------------------------------------------------------------------

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            openScreen = null;
            openRef = null;
            return;
        }
        Screen screen = minecraft.gui.screen();
        if (screen != openScreen) {
            openScreen = screen;
            openRef = screen instanceof AbstractContainerScreen<?> container && isStorage(container.getMenu())
                ? lookedAt() : null;
            readStateId = -1;
        }
        Project project = TutorialTracker.activeProject();
        boolean changed = false;
        // The open container is read again when the server sent a change, and twice a second in case one slipped by.
        if (openRef != null && screen instanceof AbstractContainerScreen<?> container
            && (container.getMenu().getStateId() != readStateId || ++ticksSinceRead >= 10)) {
            readStateId = container.getMenu().getStateId();
            ticksSinceRead = 0;
            Map<String, Integer> contents = containerContents(container.getMenu());
            lastSeen.put(key(openRef), contents);
            if (project != null) {
                Project.TrackedContainer tracked = find(project, openRef);
                if (tracked != null && (!tracked.seen || !tracked.contents.equals(contents))) {
                    tracked.contents = contents;
                    tracked.seen = true;
                    TutorialTracker.markDirty();
                    changed = true;
                }
            }
        }
        if (project != null && forgetRemoved(minecraft, project)) {
            changed = true;
        }
        // Once a second regardless, for anything no counter tells about.
        if (changed || inventoryChanged(minecraft) || ++ticksSinceCount >= 20) {
            recount();
        }
    }

    private static boolean inventoryChanged(Minecraft minecraft) {
        Inventory inventory = minecraft.player.getInventory();
        ItemStack carried = minecraft.player.containerMenu.getCarried();
        return minecraft.player != countedPlayer || inventory.getTimesChanged() != countedInventoryChanges
            || carried != countedCarried || carried.getCount() != countedCarriedCount;
    }

    private static boolean isStorage(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
    }

    /**
     * A tracked shulker box that was picked up is in an inventory now (and counted there); its old count would be
     * counted twice. Checked only where the client has the block loaded.
     */
    private static boolean forgetRemoved(Minecraft minecraft, Project project) {
        if (minecraft.level.getGameTime() % 20 != 0) {
            return false;
        }
        boolean changed = false;
        String world = worldKey();
        String dimension = dimension();
        for (Project.TrackedContainer container : project.containers) {
            if (container.ender || !container.world.equals(world) || !container.dimension.equals(dimension)
                || container.contents.isEmpty()) {
                continue;
            }
            BlockPos pos = new BlockPos(container.x, container.y, container.z);
            if (!minecraft.level.isLoaded(pos)) {
                continue;
            }
            BlockState state = minecraft.level.getBlockState(pos);
            boolean storage = state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock;
            if (!storage) {
                container.contents = new LinkedHashMap<>();
                container.seen = false;
                TutorialTracker.markDirty();
                changed = true;
            }
        }
        return changed;
    }

    private static Map<String, Integer> containerContents(AbstractContainerMenu menu) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) {
                add(counts, slot.getItem(), 0);
            }
        }
        return counts;
    }

    // --- Totals -------------------------------------------------------------------------------------------------

    public static void recount() {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Integer> inventory = new HashMap<>();
        ticksSinceCount = 0;
        countedPlayer = minecraft.player;
        if (minecraft.player != null) {
            Inventory playerInventory = minecraft.player.getInventory();
            countedInventoryChanges = playerInventory.getTimesChanged();
            for (int i = 0; i < playerInventory.getContainerSize(); i++) {
                add(inventory, playerInventory.getItem(i), 0);
            }
            // An item held on the cursor would otherwise drop out of the count while it is moved.
            ItemStack carried = minecraft.player.containerMenu.getCarried();
            countedCarried = carried;
            countedCarriedCount = carried.getCount();
            add(inventory, carried, 0);
        }
        inventoryCounts = inventory;

        Map<String, Integer> sum = new HashMap<>();
        Project project = TutorialTracker.activeProject();
        if (TutorialTracker.config().countInventory) {
            inventory.forEach((item, count) -> sum.merge(item, count, Integer::sum));
        }
        if (project != null) {
            // Only this world's containers: a project's chests in another world or server hold nothing here.
            String world = minecraft.level != null ? worldKey() : null;
            for (Project.TrackedContainer container : project.containers) {
                if (world == null || container.world.equals(world)) {
                    container.contents.forEach((item, count) -> sum.merge(item, count, Integer::sum));
                }
            }
        }
        totals = sum;
        revision++;
    }

    public static int revision() {
        return revision;
    }

    /** How many of an item the player has: assigned containers, plus the inventory when that is counted. */
    public static int have(String item) {
        return totals.getOrDefault(item, 0);
    }

    public static int inInventory(String item) {
        return inventoryCounts.getOrDefault(item, 0);
    }

    /** Counts a stack and what is inside it, read in place: no copies of the stacks in shulker boxes and bundles. */
    private static void add(Map<String, Integer> counts, ItemInstance stack, int depth) {
        if (stack == null || stack instanceof ItemStack itemStack && itemStack.isEmpty()) {
            return;
        }
        counts.merge(ItemNameIndex.id(stack.typeHolder().value()), stack.count(), Integer::sum);
        if (depth > 2) {
            return;
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStackTemplate inner : contents.nonEmptyItems()) {
                add(counts, inner, depth + 1);
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStackTemplate inner : bundle.items()) {
                add(counts, inner, depth + 1);
            }
        }
    }
}
