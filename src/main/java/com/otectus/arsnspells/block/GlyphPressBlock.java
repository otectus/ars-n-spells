package com.otectus.arsnspells.block;

import com.otectus.arsnspells.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Glyph Press workstation. Faces the player who placed it; the shape follows the model's
 * plinth, worktable, uprights, crossbar and scroll cradle, precomputed once per facing.
 */
public class GlyphPressBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** The model's solid parts for a north-facing press, in pixels; tools/generate_glyph_press.py prints them. */
    private static final double[][] NORTH_BOXES = {
        {0.5, 0, 0.5, 15.5, 1.5, 15.5},
        {1.5, 1.5, 1.5, 14.5, 4, 14.5},
        {3, 4, 3, 13, 5.5, 13},
        {1.5, 4, 11, 3.5, 13, 13},
        {12.5, 4, 11, 14.5, 13, 13},
        {1.5, 13, 11, 14.5, 14.5, 13},
        {7, 13.5, 7, 9, 14.5, 11},
        {7.5, 9.5, 7.5, 8.5, 13.5, 8.5},
        {7.3, 8.5, 7.3, 8.7, 9.5, 8.7},
        {3, 4, 1, 13, 5, 3},
        {3, 5, 1, 4, 7, 3},
        {12, 5, 1, 13, 7, 3},
    };
    /** Whether a job runs: selects the model with the lit, animated rune plate. */
    public static final BooleanProperty WORKING = BooleanProperty.create("working");
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] b : NORTH_BOXES) shape = Shapes.or(shape, rotated(b, facing));
            SHAPES.put(facing, shape.optimize());
        }
    }

    /** Rotate a north-facing box about the block centre to {@code facing}. */
    private static VoxelShape rotated(double[] b, Direction facing) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
            case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
            case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
            default -> Block.box(x0, b[1], z0, x1, b[4], z1);
        };
    }

    public GlyphPressBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WORKING, false));
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
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GlyphPressBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.GLYPH_PRESS.get()) return null;
        return (lvl, pos, st, be) -> GlyphPressBlockEntity.serverTick(lvl, pos, st, (GlyphPressBlockEntity) be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GlyphPressBlockEntity press
                && player instanceof ServerPlayer serverPlayer) {
            UUID session = UUID.randomUUID();
            var provider = new SimpleMenuProvider(
                (id, inventory, menuPlayer) -> new com.otectus.arsnspells.menu.GlyphPressMenu(id, inventory, press, session),
                press.getDisplayName());
            NetworkHooks.openScreen(serverPlayer, provider, buffer -> { buffer.writeBlockPos(pos); buffer.writeUUID(session); });
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof GlyphPressBlockEntity press) press.dropContents();
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    /**
     * Client only, and only while a job runs: a few school-coloured motes drift from the scroll
     * cradle toward the rune plate. An idle press spawns nothing.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof GlyphPressBlockEntity press) || !press.clientWorking()) return;
        int color = press.clientSchoolColor();
        Vector3f rgb = new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
        Direction facing = state.getValue(FACING);
        Vec3 center = Vec3.atBottomCenterOf(pos).add(0, 5.6 / 16.0, 0);
        Vec3 cradle = center.add(facing.getStepX() * 0.38, 1.0 / 16.0, facing.getStepZ() * 0.38);
        for (int i = 0; i < 2; i++) {
            double t = random.nextDouble();
            Vec3 point = cradle.add(center.subtract(cradle).scale(t));
            level.addParticle(new DustParticleOptions(rgb, 0.6f), point.x + (random.nextDouble() - 0.5) * 0.1,
                point.y + random.nextDouble() * 0.08, point.z + (random.nextDouble() - 0.5) * 0.1, 0, 0.01, 0);
        }
    }
}
