package com.otectus.arsnspells.block;

import com.otectus.arsnspells.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Glyph Press workstation: a dark stone base, archwood posts, a brass frame holding a scroll
 * cradle at the back and a rune plate at the front, with a small Source crystal above. Right-click
 * opens its menu. While it works ({@link #WORKING}), the plate's rune pulses and a school-coloured
 * thread runs from the scroll to the plate (client only, one particle per tick at most).
 */
public class GlyphPressBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<GlyphPressBlock> CODEC = simpleCodec(GlyphPressBlock::new);
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    /** Modelled facing north; rotated once per facing at class load. */
    private static final VoxelShape NORTH = Shapes.or(
        Block.box(1, 0, 1, 15, 4, 15),     // stone base
        Block.box(2, 4, 2, 4, 12, 4),       // archwood posts
        Block.box(12, 4, 2, 14, 12, 4),
        Block.box(2, 4, 12, 4, 12, 14),
        Block.box(12, 4, 12, 14, 12, 14),
        Block.box(3, 4, 3, 13, 6, 13),      // bed
        Block.box(4, 6, 9, 12, 9, 13),      // scroll cradle (back)
        Block.box(4, 6, 3, 12, 7, 8),       // rune plate (front)
        Block.box(2, 12, 2, 14, 13, 14),    // brass frame
        Block.box(7, 13, 7, 9, 16, 9)       // stylus and crystal
    );
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) SHAPES.put(facing, rotate(NORTH, facing));
    }

    public GlyphPressBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GlyphPressBlockEntity(pos, state);
    }

    /** Only the server ticks, and only to advance a job: an idle Press returns at its first check. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.GLYPH_PRESS.get()) return null;
        return (lvl, pos, st, be) -> GlyphPressBlockEntity.serverTick(lvl, pos, st, (GlyphPressBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GlyphPressBlockEntity press
                && player instanceof ServerPlayer serverPlayer) {
            java.util.UUID session = java.util.UUID.randomUUID();
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, menuPlayer) -> new com.otectus.arsnspells.menu.GlyphPressMenu(id, inventory, press, session),
                    press.getDisplayName()),
                buffer -> { buffer.writeBlockPos(pos); buffer.writeUUID(session); });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof GlyphPressBlockEntity press) press.dropContents();
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    /** Client: a thread of school-coloured motes from the scroll cradle to the rune plate while working. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(WORKING)) return;
        int rgb = level.getBlockEntity(pos) instanceof GlyphPressBlockEntity press ? press.color() : 0x9B6BD6;
        Direction facing = state.getValue(FACING);
        double t = random.nextDouble();
        // Cradle centre at the back, plate centre at the front, both in block-local north space.
        double fx = 0.5, fz = 0.69 - 0.38 * t, fy = 0.56 + 0.05 * Math.sin(t * Math.PI);
        double[] p = rotatePoint(fx, fz, facing);
        Vector3f color = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        level.addParticle(new DustParticleOptions(color, 0.55f), pos.getX() + p[0], pos.getY() + fy, pos.getZ() + p[1], 0, 0.01, 0);
        if (random.nextInt(4) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 0.6, 0.3, (random.nextDouble() - 0.5) * 0.6);
        }
    }

    private static double[] rotatePoint(double x, double z, Direction facing) {
        return switch (facing) {
            case SOUTH -> new double[]{1 - x, 1 - z};
            case WEST -> new double[]{z, 1 - x};
            case EAST -> new double[]{1 - z, x};
            default -> new double[]{x, z};
        };
    }

    private static VoxelShape rotate(VoxelShape shape, Direction facing) {
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double[] a = rotatePoint(x1, z1, facing);
            double[] b = rotatePoint(x2, z2, facing);
            out[0] = Shapes.or(out[0], Shapes.box(Math.min(a[0], b[0]), y1, Math.min(a[1], b[1]),
                Math.max(a[0], b[0]), y2, Math.max(a[1], b[1])));
        });
        return out[0].optimize();
    }
}
