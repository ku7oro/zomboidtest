package com.example.zomboidmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/** Генератор заброшенного города (оригинальная планировка, вдохновлено Knox Country). */
public class TownGenerator {
    public static final String[] NAMES = {"Малдро", "Вестпоинт", "Риверсайд", "Росвуд", "Мэрилэнд"};

    private static final int CELL = 16;   // размер квартала
    private static final int ROAD = 4;    // ширина дороги
    private static final int HOUSE = 7;   // размер дома
    private static final int OFFSET = 6;  // отступ дома внутри квартала
    private static final int FLAGS = Block.NOTIFY_LISTENERS;

    private static final Block[] WALLS = {
            Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
            Blocks.BRICKS, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE
    };

    public static String build(ServerWorld world, BlockPos origin, int cells) {
        Random r = world.getRandom();
        String name = NAMES[r.nextInt(NAMES.length)];

        // Дороги
        for (int x = 0; x < cells * CELL; x++) {
            for (int z = 0; z < cells * CELL; z++) {
                if (x % CELL >= ROAD && z % CELL >= ROAD) continue;
                int wx = origin.getX() + x, wz = origin.getZ() + z;
                int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                for (int dy = 1; dy <= 6; dy++) {
                    BlockPos up = new BlockPos(wx, top + dy, wz);
                    if (!world.getBlockState(up).isAir()) set(world, up, Blocks.AIR.getDefaultState());
                }
                set(world, new BlockPos(wx, top, wz), Blocks.GRAY_CONCRETE.getDefaultState());
            }
        }

        // Дома и зомби
        for (int cx = 0; cx < cells; cx++) {
            for (int cz = 0; cz < cells; cz++) {
                int cellX = origin.getX() + cx * CELL;
                int cellZ = origin.getZ() + cz * CELL;

                if (r.nextFloat() < 0.6f) {
                    int zx = cellX + 1, zz = cellZ + 1;
                    int zy = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, zx, zz);
                    spawnZombie(world, zx + 0.5, zy, zz + 0.5, r);
                }

                if (r.nextFloat() < 0.15f) continue; // пустой участок

                int hx = cellX + OFFSET, hz = cellZ + OFFSET;
                int floorY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, hx + 3, hz + 3) - 1;
                buildHouse(world, new BlockPos(hx, floorY, hz), r);
            }
        }
        return name;
    }

    private static void buildHouse(ServerWorld world, BlockPos o, Random r) {
        BlockState wall = WALLS[r.nextInt(WALLS.length)].getDefaultState();
        BlockState floor = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        int fy = o.getY();

        // очистка объёма (деревья, трава)
        for (int dx = -1; dx <= HOUSE; dx++)
            for (int dz = -1; dz <= HOUSE; dz++)
                for (int dy = 1; dy <= 7; dy++) {
                    BlockPos p = new BlockPos(o.getX() + dx, fy + dy, o.getZ() + dz);
                    if (!world.getBlockState(p).isAir()) set(world, p, air);
                }

        for (int dx = 0; dx < HOUSE; dx++) {
            for (int dz = 0; dz < HOUSE; dz++) {
                int x = o.getX() + dx, z = o.getZ() + dz;
                boolean edge = dx == 0 || dx == HOUSE - 1 || dz == 0 || dz == HOUSE - 1;
                boolean corner = (dx == 0 || dx == HOUSE - 1) && (dz == 0 || dz == HOUSE - 1);

                // фундамент
                for (int dy = 1; dy <= 5; dy++) {
                    BlockPos fp = new BlockPos(x, fy - dy, z);
                    if (!world.getBlockState(fp).isSolidBlock(world, fp)) {
                        set(world, fp, Blocks.COBBLESTONE.getDefaultState());
                    }
                }
                set(world, new BlockPos(x, fy, z), floor);

                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos p = new BlockPos(x, fy + dy, z);
                    if (!edge) { set(world, p, air); continue; }
                    boolean door = dz == 0 && dx == 3 && dy <= 2;
                    boolean window = dy == 2 && !corner
                            && ((dz == 0 || dz == HOUSE - 1) ? dx % 2 == 1 : dz % 2 == 1);
                    if (door) {
                        set(world, p, air);
                    } else if (window) {
                        set(world, p, r.nextFloat() < 0.4f ? Blocks.GLASS_PANE.getDefaultState() : air);
                    } else {
                        set(world, p, wall);
                    }
                }
                set(world, new BlockPos(x, fy + 4, z), wall); // крыша

                if (!edge && r.nextFloat() < 0.08f) {
                    set(world, new BlockPos(x, fy + 3, z), Blocks.COBWEB.getDefaultState());
                }
            }
        }

        // мебель и лут
        BlockPos chestPos = new BlockPos(o.getX() + 1, fy + 1, o.getZ() + 5);
        set(world, chestPos, Blocks.CHEST.getDefaultState());
        BlockEntity be = world.getBlockEntity(chestPos);
        if (be instanceof ChestBlockEntity chest) {
            int n = 3 + r.nextInt(4);
            for (int i = 0; i < n; i++) chest.setStack(r.nextInt(27), randomLoot(r));
        }
        set(world, new BlockPos(o.getX() + 5, fy + 1, o.getZ() + 5), Blocks.CRAFTING_TABLE.getDefaultState());

        // зомби внутри
        int zombies = 1 + r.nextInt(3);
        for (int i = 0; i < zombies; i++) {
            spawnZombie(world, o.getX() + 2 + r.nextInt(3) + 0.5, fy + 1, o.getZ() + 2 + r.nextInt(3) + 0.5, r);
        }
    }

    private static ItemStack randomLoot(Random r) {
        return switch (r.nextInt(14)) {
            case 0, 1 -> new ItemStack(ZomboidMod.BANDAGE, 1 + r.nextInt(3));
            case 2 -> new ItemStack(ZomboidMod.CLEAN_WATER, 1 + r.nextInt(2));
            case 3, 4 -> new ItemStack(ZomboidMod.CANNED_BEANS, 1 + r.nextInt(3));
            case 5 -> new ItemStack(ZomboidMod.PAINKILLERS);
            case 6 -> new ItemStack(Items.BREAD, 1 + r.nextInt(2));
            case 7 -> new ItemStack(Items.APPLE, 1 + r.nextInt(3));
            case 8 -> new ItemStack(Items.STICK, 2 + r.nextInt(5));
            case 9 -> new ItemStack(Items.IRON_NUGGET, 2 + r.nextInt(6));
            case 10 -> new ItemStack(Items.COAL, 1 + r.nextInt(4));
            case 11 -> new ItemStack(Items.WHITE_WOOL, 1 + r.nextInt(3));
            case 12 -> r.nextInt(4) == 0 ? new ItemStack(ZomboidMod.NAIL_BAT) : new ItemStack(Items.STRING, 2);
            default -> new ItemStack(Items.TORCH, 2 + r.nextInt(4));
        };
    }

    private static void spawnZombie(ServerWorld world, double x, double y, double z, Random r) {
        ZombieEntity zombie = EntityType.ZOMBIE.create(world);
        if (zombie == null) return;
        zombie.refreshPositionAndAngles(x, y, z, r.nextFloat() * 360f, 0f);
        zombie.setPersistent();
        world.spawnEntity(zombie);
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, FLAGS);
    }
}
